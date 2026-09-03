package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.config.FadadaProperties;
import com.tradepass.dto.response.FadadaCompanyIdentityPayload;
import com.tradepass.dto.response.ServiceUrlPayload;
import com.tradepass.entity.Company;
import com.tradepass.entity.FadadaCorpIdentity;
import com.tradepass.entity.FadadaCorpSeal;
import com.tradepass.integration.fadada.FadadaCompanyGateway;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.FadadaCorpIdentityMapper;
import com.tradepass.mapper.FadadaCorpSealMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class FadadaCompanyService {
    private static final List<String> AUTH_SCOPES = List.of(
            "ident_info", "seal_info", "signtask_init", "signtask_info", "signtask_file");
    private final FadadaCorpIdentityMapper identityMapper;
    private final FadadaCorpSealMapper sealMapper;
    private final CompanyMapper companyMapper;
    private final AccessControlService accessControl;
    private final CompanyCertificationService certificationService;
    private final FadadaPersonalIdentityService personalIdentityService;
    private final FadadaCompanyGateway gateway;
    private final FadadaProperties properties;
    private final ObjectMapper objectMapper;

    public FadadaCompanyService(FadadaCorpIdentityMapper identityMapper,
                                FadadaCorpSealMapper sealMapper,
                                CompanyMapper companyMapper,
                                AccessControlService accessControl,
                                CompanyCertificationService certificationService,
                                FadadaPersonalIdentityService personalIdentityService,
                                FadadaCompanyGateway gateway,
                                FadadaProperties properties,
                                ObjectMapper objectMapper) {
        this.identityMapper = identityMapper;
        this.sealMapper = sealMapper;
        this.companyMapper = companyMapper;
        this.accessControl = accessControl;
        this.certificationService = certificationService;
        this.personalIdentityService = personalIdentityService;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public FadadaCompanyIdentityPayload current(long companyId) {
        accessControl.requireLegalOrClaim(companyId);
        return payload(companyId, find(companyId));
    }

    @Transactional
    public ServiceUrlPayload createAuthUrl(long companyId) {
        requireReady();
        accessControl.requireLegalOrClaim(companyId);
        personalIdentityService.requireCurrentVerified();
        Company company = requireCompany(companyId);
        requireCompanyFields(company);
        FadadaCorpIdentity identity = ensure(companyId, AuthContext.userId());
        String url = gateway.createAuthUrl(new FadadaCompanyGateway.AuthCommand(
                identity.getClientCorpId(), "tradepass-user-" + AuthContext.userId(),
                company.getName(), company.getCreditCode(), AUTH_SCOPES, properties.getCallbackUrl(),
                "/pages/service-return/service-return?scene=company&companyId=" + companyId));
        validateUrl(url);
        identity.setLocalStatus("IN_PROGRESS");
        identity.setFailureReason("");
        identity.setSubmittedAt(LocalDateTime.now());
        identityMapper.updateById(identity);
        companyMapper.update(new LambdaUpdateWrapper<Company>().eq(Company::getId, companyId)
                .set(Company::getCertificationStatus, "PENDING_REVIEW"));
        return new ServiceUrlPayload(url, "company", identity.getLocalStatus());
    }

    @Transactional
    public FadadaCompanyIdentityPayload syncCurrent(long companyId) {
        requireReady();
        accessControl.requireLegalOrClaim(companyId);
        return sync(companyId);
    }

    @Transactional
    public FadadaCompanyIdentityPayload sync(long companyId) {
        FadadaCorpIdentity identity = find(companyId);
        if (identity == null) return payload(companyId, null);
        Company company = requireCompany(companyId);
        FadadaCompanyGateway.CompanyAccount account = gateway.getCompany(
                identity.getClientCorpId(), identity.getOpenCorpId());
        if (hasText(account.openCorpId())) identity.setOpenCorpId(account.openCorpId());
        if (hasText(account.bindingStatus())) identity.setBindingStatus(account.bindingStatus());
        if (hasText(account.identStatus())) identity.setIdentStatus(account.identStatus());
        if (account.authScopes() != null) identity.setAuthScopes(json(account.authScopes()));
        if ("identified".equalsIgnoreCase(identity.getIdentStatus()) && hasText(identity.getOpenCorpId())) {
            FadadaCompanyGateway.CompanyIdentity detail = gateway.getIdentity(identity.getOpenCorpId());
            verifyMatches(company, detail);
            identity.setIdentStatus(detail.identStatus());
            identity.setVerifiedName(detail.companyName());
            identity.setVerifiedCreditCode(detail.creditCode());
            identity.setVerifiedLegalRepName(detail.legalRepName());
            identity.setIdentMethod(detail.identMethod());
            identity.setVerifiedAt(parseTime(detail.verifiedAt(), LocalDateTime.now()));
            identity.setLocalStatus("VERIFIED");
            identity.setFailureReason("");
            certificationService.completeProviderCertification(companyId, identity.getApplicantUserId(),
                    "FDD-CORP-" + identity.getClientCorpId(), "企业认证已完成");
            syncSeals(identity);
        } else if ("authorized".equalsIgnoreCase(identity.getBindingStatus())) {
            identity.setLocalStatus("IN_PROGRESS");
        }
        identity.setLastSyncAt(LocalDateTime.now());
        identityMapper.updateById(identity);
        return payload(companyId, identity);
    }

    @Transactional
    public FadadaCompanyIdentityPayload syncByClientCorpId(String clientCorpId) {
        FadadaCorpIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<FadadaCorpIdentity>()
                .eq(FadadaCorpIdentity::getClientCorpId, clientCorpId).last("LIMIT 1"));
        return identity == null ? null : sync(identity.getCompanyId());
    }

    @Transactional
    public FadadaCompanyIdentityPayload syncByOpenCorpId(String openCorpId) {
        FadadaCorpIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<FadadaCorpIdentity>()
                .eq(FadadaCorpIdentity::getOpenCorpId, openCorpId).last("LIMIT 1"));
        return identity == null ? null : sync(identity.getCompanyId());
    }

    @Transactional
    public FadadaCompanyIdentityPayload syncCallback(String clientCorpId, String openCorpId, JsonNode data) {
        FadadaCorpIdentity identity = hasText(clientCorpId)
                ? identityMapper.selectOne(new LambdaQueryWrapper<FadadaCorpIdentity>()
                    .eq(FadadaCorpIdentity::getClientCorpId, clientCorpId).last("LIMIT 1"))
                : identityMapper.selectOne(new LambdaQueryWrapper<FadadaCorpIdentity>()
                    .eq(FadadaCorpIdentity::getOpenCorpId, openCorpId).last("LIMIT 1"));
        if (identity == null) return null;
        if (hasText(openCorpId)) identity.setOpenCorpId(openCorpId);
        String authResult = callbackText(data, "authResult");
        String process = callbackText(data, "corpIdentProcessStatus", "verifyStatus");
        String reason = callbackText(data, "corpIdentFailedReason", "authFailedReason");
        if ("fail".equalsIgnoreCase(authResult) || "failed".equalsIgnoreCase(authResult)
                || "failed".equalsIgnoreCase(process)) {
            identity.setLocalStatus("FAILED");
            identity.setFailureReason(hasText(reason) ? reason : "企业认证未通过");
            identity.setLastSyncAt(LocalDateTime.now());
            identityMapper.updateById(identity);
            companyMapper.update(new LambdaUpdateWrapper<Company>().eq(Company::getId, identity.getCompanyId())
                    .set(Company::getCertificationStatus, "REJECTED"));
            return payload(identity.getCompanyId(), identity);
        }
        identityMapper.updateById(identity);
        return sync(identity.getCompanyId());
    }

    @Transactional
    public ServiceUrlPayload createSealManageUrl(long companyId) {
        requireReady();
        accessControl.requireLegalOrClaim(companyId);
        FadadaCorpIdentity identity = requireVerified(companyId);
        String url = gateway.createSealManageUrl(identity.getOpenCorpId(),
                "tradepass-user-" + AuthContext.userId(), "");
        validateUrl(url);
        return new ServiceUrlPayload(url, "seal", identity.getLocalStatus());
    }

    public FadadaCorpIdentity requireVerified(long companyId) {
        FadadaCorpIdentity identity = find(companyId);
        Company company = companyMapper.selectById(companyId);
        if (identity == null || company == null || !"VERIFIED".equals(company.getCertificationStatus())
                || !"VERIFIED".equals(identity.getLocalStatus()) || !hasText(identity.getOpenCorpId())) {
            throw new BusinessException("请先完成企业认证");
        }
        if (!normalize(company.getName()).equals(normalize(identity.getVerifiedName()))
                || !normalize(company.getCreditCode()).equals(normalize(identity.getVerifiedCreditCode()))) {
            throw new BusinessException("企业信息与认证记录不一致，请重新核验企业认证");
        }
        for (String scope : AUTH_SCOPES) {
            if (!hasText(identity.getAuthScopes()) || !identity.getAuthScopes().contains("\"" + scope + "\"")) {
                throw new BusinessException("企业电子签授权不完整，请重新进入企业认证完成授权");
            }
        }
        return identity;
    }

    public String enabledSealId(long companyId) {
        FadadaCorpSeal seal = sealMapper.selectOne(new LambdaQueryWrapper<FadadaCorpSeal>()
                .eq(FadadaCorpSeal::getCompanyId, companyId)
                .eq(FadadaCorpSeal::getSealStatus, "enable")
                .orderByAsc(FadadaCorpSeal::getId).last("LIMIT 1"));
        if (seal == null) throw new BusinessException("请先启用企业电子印章");
        return seal.getSealId();
    }

    private void syncSeals(FadadaCorpIdentity identity) {
        List<FadadaCompanyGateway.SealInfo> remote = gateway.listSeals(identity.getOpenCorpId());
        LocalDateTime now = LocalDateTime.now();
        sealMapper.update(null, new LambdaUpdateWrapper<FadadaCorpSeal>()
                .eq(FadadaCorpSeal::getCompanyId, identity.getCompanyId())
                .set(FadadaCorpSeal::getSealStatus, "disable")
                .set(FadadaCorpSeal::getLastSyncAt, now));
        for (FadadaCompanyGateway.SealInfo value : remote) {
            if (!hasText(value.sealId())) continue;
            FadadaCorpSeal seal = sealMapper.selectOne(new LambdaQueryWrapper<FadadaCorpSeal>()
                    .eq(FadadaCorpSeal::getCompanyId, identity.getCompanyId())
                    .eq(FadadaCorpSeal::getSealId, value.sealId()).last("LIMIT 1"));
            if (seal == null) {
                seal = new FadadaCorpSeal();
                seal.setCompanyId(identity.getCompanyId());
                seal.setSealId(value.sealId());
                seal.setSealName(value.sealName());
                seal.setCategoryType(value.categoryType());
                seal.setSealStatus(value.status());
                seal.setLastSyncAt(now);
                sealMapper.insert(seal);
            } else {
                seal.setSealName(value.sealName());
                seal.setCategoryType(value.categoryType());
                seal.setSealStatus(value.status());
                seal.setLastSyncAt(now);
                sealMapper.updateById(seal);
            }
        }
        boolean enabled = remote.stream().anyMatch(value -> "enable".equalsIgnoreCase(value.status()));
        companyMapper.update(new LambdaUpdateWrapper<Company>().eq(Company::getId, identity.getCompanyId())
                .set(Company::getSealStatus, enabled ? "VERIFIED" : "NOT_STARTED"));
    }

    private FadadaCorpIdentity ensure(long companyId, long userId) {
        FadadaCorpIdentity identity = find(companyId);
        if (identity != null) return identity;
        identity = new FadadaCorpIdentity();
        identity.setCompanyId(companyId);
        identity.setApplicantUserId(userId);
        identity.setClientCorpId("tradepass-company-" + companyId);
        identity.setLocalStatus("NOT_STARTED");
        identity.setBindingStatus("unauthorized");
        identity.setIdentStatus("unidentified");
        identity.setAuthScopes(json(AUTH_SCOPES));
        identityMapper.insert(identity);
        return identity;
    }

    private FadadaCorpIdentity find(long companyId) {
        return identityMapper.selectOne(new LambdaQueryWrapper<FadadaCorpIdentity>()
                .eq(FadadaCorpIdentity::getCompanyId, companyId).last("LIMIT 1"));
    }

    private FadadaCompanyIdentityPayload payload(long companyId, FadadaCorpIdentity identity) {
        List<FadadaCompanyIdentityPayload.SealPayload> seals = sealMapper.selectList(
                        new LambdaQueryWrapper<FadadaCorpSeal>().eq(FadadaCorpSeal::getCompanyId, companyId)
                                .orderByAsc(FadadaCorpSeal::getId))
                .stream().map(value -> new FadadaCompanyIdentityPayload.SealPayload(
                        value.getSealId(), value.getSealName(), value.getCategoryType(), value.getSealStatus())).toList();
        int enabled = (int) seals.stream().filter(value -> "enable".equalsIgnoreCase(value.status())).count();
        String status = identity == null ? "NOT_STARTED" : identity.getLocalStatus();
        return new FadadaCompanyIdentityPayload(properties.isEnabled(), String.valueOf(companyId), status,
                statusText(status), identity == null ? null : identity.getVerifiedName(),
                identity == null ? null : identity.getVerifiedCreditCode(),
                identity == null || !hasText(identity.getFailureReason()) ? null : identity.getFailureReason(),
                enabled, seals, identity == null || identity.getLastSyncAt() == null
                ? null : identity.getLastSyncAt().toString());
    }

    private void verifyMatches(Company company, FadadaCompanyGateway.CompanyIdentity detail) {
        if (!normalize(company.getName()).equals(normalize(detail.companyName()))
                || !normalize(company.getCreditCode()).equals(normalize(detail.creditCode()))) {
            throw new BusinessException("认证信息与当前企业名称或统一社会信用代码不一致");
        }
    }

    private void requireCompanyFields(Company company) {
        if (!hasText(company.getName()) || !hasText(company.getCreditCode())) {
            throw new BusinessException("请先完善企业名称和统一社会信用代码");
        }
    }

    private Company requireCompany(long companyId) {
        Company company = companyMapper.selectByIdForUpdate(companyId);
        if (company == null) throw new BusinessException("企业不存在");
        return company;
    }

    private void requireReady() {
        if (!properties.isEnabled() || !hasText(properties.getAppId()) || !hasText(properties.getAppSecret())
                || !hasText(properties.getServerUrl()) || !hasText(properties.getCallbackUrl())) {
            throw new BusinessException("电子签服务尚未配置完整");
        }
    }

    private void validateUrl(String value) {
        if (!hasText(value) || !value.startsWith("https://")) throw new BusinessException("电子签服务地址无效");
    }

    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new BusinessException("认证授权范围保存失败"); }
    }

    private LocalDateTime parseTime(String value, LocalDateTime fallback) {
        if (!hasText(value)) return fallback;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
            try { return LocalDateTime.parse(value, formatter); } catch (RuntimeException ignored) { }
        }
        return fallback;
    }

    private String statusText(String status) {
        return switch (status == null ? "NOT_STARTED" : status) {
            case "IN_PROGRESS" -> "认证中";
            case "VERIFIED" -> "已认证";
            case "FAILED" -> "认证未通过";
            default -> "待认证";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
    private String callbackText(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && hasText(value.asText())) return value.asText().trim();
        }
        return null;
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
