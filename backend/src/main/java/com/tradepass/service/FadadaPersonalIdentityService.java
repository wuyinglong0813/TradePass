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
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.entity.FadadaUserIdentity;
import com.tradepass.entity.SysUser;
import com.tradepass.integration.fadada.FadadaUserGateway;
import com.tradepass.mapper.FadadaCallbackEventMapper;
import com.tradepass.mapper.FadadaUserIdentityMapper;
import com.tradepass.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class FadadaPersonalIdentityService {
    private static final String AUTH_SCOPE = "ident_info";
    private static final String SUBJECT_TYPE = "USER";
    private final FadadaUserIdentityMapper identityMapper;
    private final FadadaCallbackEventMapper callbackEventMapper;
    private final SysUserMapper userMapper;
    private final FadadaUserGateway gateway;
    private final FadadaProperties properties;
    private final ObjectMapper objectMapper;

    public FadadaPersonalIdentityService(FadadaUserIdentityMapper identityMapper,
                                         FadadaCallbackEventMapper callbackEventMapper,
                                         SysUserMapper userMapper,
                                         FadadaUserGateway gateway,
                                         FadadaProperties properties,
                                         ObjectMapper objectMapper) {
        this.identityMapper = identityMapper;
        this.callbackEventMapper = callbackEventMapper;
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
        return toPayload(sync(identity));
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
        FadadaUserGateway.AuthUrlResult result = gateway.createAuthUrl(new FadadaUserGateway.AuthUrlCommand(
                identity.getClientUserId(), user.getPhone(), callbackUrl(),
                properties.getRedirectUrl(), properties.getRedirectMiniAppUrl()));
        validateAuthUrl(result.authUrl());
        identity.setLocalStatus("IN_PROGRESS");
        identity.setIdentProcessStatus("identifying");
        identity.setFailureReason("");
        identity.setIdentSubmittedAt(LocalDateTime.now());
        identityMapper.updateById(identity);
        return new FadadaAuthUrlPayload(result.authUrl(), toPayload(identity));
    }

    public void handleCallback(String suppliedToken, JsonNode payload) {
        requireCallbackToken(suppliedToken);
        if (payload == null || payload.isNull()) throw new BusinessException("法大大回调内容为空");
        JsonNode eventData = eventData(payload);
        String eventType = firstText(payload, "eventType", "eventCode", "type");
        if (eventType == null) eventType = firstText(eventData, "eventType", "eventCode", "type");
        String clientUserId = firstText(eventData, "clientUserId");
        if (clientUserId == null) clientUserId = firstText(payload, "clientUserId");
        String payloadHash = sha256(payload.toString());
        String eventId = firstText(payload, "eventId", "id");
        if (eventId == null) eventId = firstText(eventData, "eventId");
        if (eventId == null) eventId = "sha256:" + payloadHash;
        if (eventType == null && clientUserId != null) eventType = "user-authorize";
        if (eventType == null) eventType = "unknown";

        FadadaCallbackEvent event = receiveEvent(eventId, eventType, payloadHash);
        if ("PROCESSED".equals(event.getStatus()) || "IGNORED".equals(event.getStatus())) return;
        if (!"user-authorize".equals(eventType)) {
            finishEvent(event, "IGNORED", null, null);
            return;
        }
        if (clientUserId == null || clientUserId.isBlank()) {
            finishEvent(event, "FAILED", null, "回调缺少 clientUserId");
            throw new BusinessException("法大大回调缺少用户标识");
        }
        FadadaUserIdentity identity = findByClientUserId(clientUserId);
        if (identity == null) {
            finishEvent(event, "IGNORED", null, "本地用户绑定不存在");
            return;
        }
        try {
            applyCallbackHints(identity, eventData);
            FadadaUserIdentity synced = sync(identity);
            finishEvent(event, "PROCESSED", synced.getUserId(), null);
        } catch (RuntimeException exception) {
            finishEvent(event, "FAILED", identity.getUserId(), safeMessage(exception));
            throw exception;
        }
    }

    private FadadaUserIdentity sync(FadadaUserIdentity identity) {
        FadadaUserGateway.UserAccountResult account = gateway.getUser(
                identity.getClientUserId(), identity.getOpenUserId());
        if (hasText(account.clientUserId()) && !identity.getClientUserId().equals(account.clientUserId())) {
            throw new BusinessException("法大大用户绑定与当前账号不一致");
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
            identity.setIdentVerifiedAt(parseTime(detail.identSuccessTime(), LocalDateTime.now()));
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

    private void applyCallbackHints(FadadaUserIdentity identity, JsonNode data) {
        String openUserId = firstText(data, "openUserId", "existOpenUserId");
        String process = firstText(data, "identProcessStatus", "verifyStatus");
        String method = firstText(data, "identMethod");
        String authResult = firstText(data, "authResult");
        String failureReason = firstText(data, "identFailedReason", "authFailedReason");
        if (hasText(openUserId)) identity.setOpenUserId(openUserId);
        if (hasText(process)) identity.setIdentProcessStatus(process);
        if (hasText(method)) identity.setIdentMethod(method);
        List<String> scopes = stringList(data.get("authScope"));
        if (!scopes.isEmpty()) identity.setAuthScopes(json(scopes));
        if ("fail".equalsIgnoreCase(authResult) || "failed".equalsIgnoreCase(process)) {
            identity.setLocalStatus("FAILED");
            identity.setFailureReason(hasText(failureReason) ? failureReason : "个人认证未通过");
        } else if (!"VERIFIED".equals(identity.getLocalStatus())) {
            identity.setLocalStatus("IN_PROGRESS");
        }
        identityMapper.updateById(identity);
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

    private FadadaCallbackEvent receiveEvent(String eventId, String eventType, String payloadHash) {
        FadadaCallbackEvent existing = callbackEventMapper.selectOne(new LambdaQueryWrapper<FadadaCallbackEvent>()
                .eq(FadadaCallbackEvent::getEventId, eventId).last("LIMIT 1"));
        if (existing != null) return existing;
        FadadaCallbackEvent event = new FadadaCallbackEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setSubjectType(SUBJECT_TYPE);
        event.setPayloadSha256(payloadHash);
        event.setStatus("RECEIVED");
        event.setReceivedAt(LocalDateTime.now());
        try {
            callbackEventMapper.insert(event);
            return event;
        } catch (DuplicateKeyException duplicate) {
            return callbackEventMapper.selectOne(new LambdaQueryWrapper<FadadaCallbackEvent>()
                    .eq(FadadaCallbackEvent::getEventId, eventId).last("LIMIT 1"));
        }
    }

    private void finishEvent(FadadaCallbackEvent event, String status, Long subjectId, String reason) {
        event.setStatus(status);
        event.setSubjectId(subjectId);
        event.setFailureReason(reason == null ? "" : reason);
        event.setProcessedAt(LocalDateTime.now());
        callbackEventMapper.updateById(event);
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
        if (!properties.isEnabled()) throw new BusinessException("法大大个人认证服务尚未启用");
        if (!hasText(properties.getAppId()) || !hasText(properties.getAppSecret())
                || !hasText(properties.getServerUrl())) {
            throw new BusinessException("法大大个人认证配置不完整");
        }
        if (hasText(properties.getCallbackUrl()) != hasText(properties.getCallbackToken())) {
            throw new BusinessException("法大大回调配置不完整");
        }
    }

    private void requireCallbackToken(String suppliedToken) {
        requireReady();
        if (!hasText(properties.getCallbackUrl())) {
            throw new BusinessException("法大大回调服务尚未配置");
        }
        byte[] expected = properties.getCallbackToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = suppliedToken == null ? new byte[0] : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) throw new BusinessException("法大大回调凭证无效");
    }

    private String callbackUrl() {
        if (!hasText(properties.getCallbackUrl())) return null;
        String separator = properties.getCallbackUrl().contains("?") ? "&" : "?";
        return properties.getCallbackUrl() + separator + "token="
                + URLEncoder.encode(properties.getCallbackToken(), StandardCharsets.UTF_8);
    }

    private void validateAuthUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hasText(uri.getHost())) {
                throw new BusinessException("法大大返回的个人认证地址不安全");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("法大大返回的个人认证地址格式不正确");
        }
    }

    private JsonNode eventData(JsonNode root) {
        for (String key : List.of("data", "eventData", "callbackData", "bizContent")) {
            JsonNode value = root.get(key);
            if (value == null || value.isNull()) continue;
            if (value.isObject()) return value;
            if (value.isTextual()) {
                try {
                    JsonNode parsed = objectMapper.readTree(value.asText());
                    if (parsed != null && parsed.isObject()) return parsed;
                } catch (JsonProcessingException ignored) {
                    // Keep inspecting the root payload; malformed envelopes are rejected below.
                }
            }
        }
        return root;
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && hasText(value.asText())) return value.asText().trim();
        }
        return null;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) return values;
        node.forEach(value -> {
            if (value.isTextual() && hasText(value.asText())) values.add(value.asText());
        });
        return values;
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("认证授权范围序列化失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("回调摘要计算失败", exception);
        }
    }

    private LocalDateTime parseTime(String value, LocalDateTime fallback) {
        if (!hasText(value)) return fallback;
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

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return hasText(message) ? message.substring(0, Math.min(message.length(), 512)) : "回调处理失败";
    }

    private String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
