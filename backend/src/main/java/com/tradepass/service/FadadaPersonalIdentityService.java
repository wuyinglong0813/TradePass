package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.config.FadadaProperties;
import com.tradepass.dto.response.FadadaAuthUrlPayload;
import com.tradepass.dto.response.PersonalIdentityPayload;
import com.tradepass.entity.FadadaUserIdentity;
import com.tradepass.entity.SysUser;
import com.tradepass.integration.fadada.FadadaUserGateway;
import com.tradepass.mapper.FadadaUserIdentityMapper;
import com.tradepass.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class FadadaPersonalIdentityService {
    private static final String AUTH_SCOPE = "ident_info";
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final FadadaUserIdentityMapper identityMapper;
    private final SysUserMapper userMapper;
    private final FadadaUserGateway gateway;
    private final FadadaProperties properties;
    private final ObjectMapper objectMapper;

    public FadadaPersonalIdentityService(FadadaUserIdentityMapper identityMapper,
                                         SysUserMapper userMapper,
                                         FadadaUserGateway gateway,
                                         FadadaProperties properties,
                                         ObjectMapper objectMapper) {
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PersonalIdentityPayload current() {
        return toPayload(findByUserId(AuthContext.userId()));
    }

    @Transactional
    public PersonalIdentityPayload syncCurrent() {
        requireReady();
        FadadaUserIdentity identity = findByUserId(AuthContext.userId());
        if (identity == null) return toPayload(null);
        if ("VERIFIED".equals(identity.getLocalStatus())) return toPayload(identity);
        return toPayload(sync(identity));
    }

    @Transactional
    public PersonalIdentityPayload requireCurrentVerified() {
        requireReady();
        FadadaUserIdentity identity = findByUserId(AuthContext.userId());
        if (identity != null && !"VERIFIED".equals(identity.getLocalStatus())) {
            identity = sync(identity);
        }
        if (identity == null || !"VERIFIED".equals(identity.getLocalStatus())) {
            throw new BusinessException("请先完成个人实名认证，再创建或认证企业");
        }
        return toPayload(identity);
    }

    @Transactional
    public FadadaAuthUrlPayload createAuthUrl() {
        requireReady();
        long userId = AuthContext.userId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("当前用户不存在");
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException("请先绑定手机号，再进行个人认证");
        }
        FadadaUserIdentity identity = ensureIdentity(userId);
        // Internal mini-program routes are not provider return URLs. The client polls
        // identity status and refreshes on return, so no redirect URL is required here.
        FadadaUserGateway.AuthUrlResult result = gateway.createAuthUrl(new FadadaUserGateway.AuthUrlCommand(
                identity.getClientUserId(), user.getPhone(), callbackUrl(),
                null, null));
        validateAuthUrl(result.authUrl());
        identity.setLocalStatus("IN_PROGRESS");
        identity.setIdentProcessStatus("identifying");
        identity.setFailureReason("");
        identity.setIdentSubmittedAt(LocalDateTime.now());
        identityMapper.updateById(identity);
        return new FadadaAuthUrlPayload(result.authUrl(), toPayload(identity));
    }

    @Transactional
    public PersonalIdentityPayload syncByClientUserId(String clientUserId) {
        FadadaUserIdentity identity = findByClientUserId(clientUserId);
        return identity == null ? null : toPayload(sync(identity));
    }

    @Transactional
    public PersonalIdentityPayload syncCallback(String clientUserId, JsonNode data) {
        FadadaUserIdentity identity = findByClientUserId(clientUserId);
        if (identity == null) return null;
        String openUserId = callbackText(data, "openUserId", "existOpenUserId");
        String process = callbackText(data, "identProcessStatus", "verifyStatus");
        String method = callbackText(data, "identMethod");
        String authResult = callbackText(data, "authResult");
        String failureReason = callbackText(data, "identFailedReason", "authFailedReason");
        if (hasText(openUserId)) identity.setOpenUserId(openUserId);
        if (hasText(process)) identity.setIdentProcessStatus(process);
        if (hasText(method)) identity.setIdentMethod(method);
        if ("fail".equalsIgnoreCase(authResult) || "failed".equalsIgnoreCase(authResult)
                || "failed".equalsIgnoreCase(process)) {
            identity.setLocalStatus("FAILED");
            identity.setFailureReason(hasText(failureReason) ? failureReason : "个人认证未通过");
            identity.setLastSyncAt(LocalDateTime.now());
            identityMapper.updateById(identity);
            return toPayload(identity);
        }
        identityMapper.updateById(identity);
        return toPayload(sync(identity));
    }

    private FadadaUserIdentity sync(FadadaUserIdentity identity) {
        FadadaUserGateway.UserAccountResult account = gateway.getUser(
                identity.getClientUserId(), identity.getOpenUserId());
        if (hasText(account.clientUserId()) && !identity.getClientUserId().equals(account.clientUserId())) {
            throw new BusinessException("认证服务用户绑定与当前账号不一致");
        }
        if (hasText(account.openUserId())) identity.setOpenUserId(account.openUserId());
        if (hasText(account.bindingStatus())) identity.setBindingStatus(account.bindingStatus());
        if (hasText(account.identStatus())) identity.setIdentStatus(account.identStatus());
        if (account.authScopes() != null) identity.setAuthScopes(json(account.authScopes()));

        if ("identified".equalsIgnoreCase(identity.getIdentStatus()) && hasText(identity.getOpenUserId())) {
            FadadaUserGateway.UserIdentityResult detail = gateway.getIdentityInfo(identity.getOpenUserId());
            if (hasText(detail.identStatus())) identity.setIdentStatus(detail.identStatus());
            if (hasText(detail.userName())) identity.setVerifiedName(detail.userName());
            if (hasText(detail.identMethod())) identity.setIdentMethod(detail.identMethod());
            identity.setIdentSubmittedAt(parseTime(detail.identSubmitTime(), identity.getIdentSubmittedAt()));
            identity.setIdentVerifiedAt(parseTime(detail.identSuccessTime(), beijingNow()));
        }
        identity.setLocalStatus(resolveLocalStatus(identity));
        if ("VERIFIED".equals(identity.getLocalStatus())) {
            identity.setIdentProcessStatus("success");
            identity.setFailureReason("");
        }
        identity.setLastSyncAt(LocalDateTime.now());
        identityMapper.updateById(identity);
        return identity;
    }

    private FadadaUserIdentity ensureIdentity(long userId) {
        FadadaUserIdentity existing = findByUserId(userId);
        if (existing != null) return existing;
        FadadaUserIdentity identity = new FadadaUserIdentity();
        identity.setUserId(userId);
        identity.setClientUserId("tradepass-user-" + userId);
        identity.setLocalStatus("NOT_STARTED");
        identity.setBindingStatus("unauthorized");
        identity.setIdentStatus("unidentified");
        identity.setIdentProcessStatus("no_start");
        identity.setAuthScopes(json(List.of(AUTH_SCOPE)));
        identityMapper.insert(identity);
        return identity;
    }

    private FadadaUserIdentity findByUserId(long userId) {
        return identityMapper.selectOne(new LambdaQueryWrapper<FadadaUserIdentity>()
                .eq(FadadaUserIdentity::getUserId, userId).last("LIMIT 1"));
    }

    private FadadaUserIdentity findByClientUserId(String clientUserId) {
        return identityMapper.selectOne(new LambdaQueryWrapper<FadadaUserIdentity>()
                .eq(FadadaUserIdentity::getClientUserId, clientUserId).last("LIMIT 1"));
    }

    private PersonalIdentityPayload toPayload(FadadaUserIdentity identity) {
        if (identity == null) {
            return new PersonalIdentityPayload(properties.isEnabled(), "NOT_STARTED", "待认证",
                    "unauthorized", "unidentified", "no_start", null, null,
                    null, null, null, null);
        }
        String status = hasText(identity.getLocalStatus()) ? identity.getLocalStatus() : "NOT_STARTED";
        return new PersonalIdentityPayload(properties.isEnabled(), status, statusText(status),
                identity.getBindingStatus(), identity.getIdentStatus(), identity.getIdentProcessStatus(),
                identity.getVerifiedName(), identity.getIdentMethod(),
                hasText(identity.getFailureReason()) ? identity.getFailureReason() : null,
                text(identity.getIdentSubmittedAt()), text(identity.getIdentVerifiedAt()), text(identity.getLastSyncAt()));
    }

    private String resolveLocalStatus(FadadaUserIdentity identity) {
        if ("identified".equalsIgnoreCase(identity.getIdentStatus())
                && "authorized".equalsIgnoreCase(identity.getBindingStatus())) return "VERIFIED";
        if ("failed".equalsIgnoreCase(identity.getIdentProcessStatus())) return "FAILED";
        if ("IN_PROGRESS".equals(identity.getLocalStatus()) || hasText(identity.getOpenUserId())
                || "authorized".equalsIgnoreCase(identity.getBindingStatus())) return "IN_PROGRESS";
        return "NOT_STARTED";
    }

    private String statusText(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "认证中";
            case "VERIFIED" -> "已认证";
            case "FAILED" -> "认证未通过";
            default -> "待认证";
        };
    }

    private void requireReady() {
        if (!properties.isEnabled()) throw new BusinessException("个人认证服务尚未启用");
        if (!hasText(properties.getAppId()) || !hasText(properties.getAppSecret())
                || !hasText(properties.getServerUrl())) {
            throw new BusinessException("个人认证服务配置不完整");
        }
    }

    private String callbackUrl() {
        if (!hasText(properties.getCallbackUrl())) return null;
        String value = properties.getCallbackUrl().trim();
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hasText(uri.getHost())
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("认证回调地址配置不正确，请联系管理员检查 HTTPS 地址");
        }
    }

    private void validateAuthUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hasText(uri.getHost())) {
                throw new BusinessException("认证服务返回的个人认证地址不安全");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("认证服务返回的个人认证地址格式不正确");
        }
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("认证授权范围序列化失败", exception);
        }
    }

    private LocalDateTime parseTime(String value, LocalDateTime fallback) {
        if (!hasText(value)) return fallback;
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(BEIJING_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Continue with provider formats that do not carry an explicit offset.
        }
        try {
            return Instant.parse(value).atZone(BEIJING_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Continue with provider formats that are documented as Beijing local time.
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next documented/common provider timestamp format.
            }
        }
        return fallback;
    }

    private String text(LocalDateTime value) {
        return value == null ? null : DISPLAY_TIME.format(value);
    }

    private LocalDateTime beijingNow() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    private String callbackText(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && hasText(value.asText())) return value.asText().trim();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
