package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.dto.request.CertificationReviewRequest;
import com.tradepass.dto.response.CertificationApplicationPayload;
import com.tradepass.entity.Company;
import com.tradepass.entity.CompanyCertificationApplication;
import com.tradepass.entity.CompanyMember;
import com.tradepass.mapper.CompanyCertificationApplicationMapper;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CompanyCertificationService {
    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final CompanyCertificationApplicationMapper applicationMapper;
    private final AccessControlService accessControlService;
    private final TenantBootstrapService tenantBootstrapService;
    private final AuditLogService auditLogService;
    private final boolean autoApprove;
    private final String callbackToken;

    public CompanyCertificationService(CompanyMapper companyMapper,
                                       CompanyMemberMapper companyMemberMapper,
                                       CompanyCertificationApplicationMapper applicationMapper,
                                       AccessControlService accessControlService,
                                       TenantBootstrapService tenantBootstrapService,
                                       AuditLogService auditLogService,
                                       @Value("${tradepass.verification.auto-approve:false}") boolean autoApprove,
                                       @Value("${tradepass.verification.callback-token:}") String callbackToken) {
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.applicationMapper = applicationMapper;
        this.accessControlService = accessControlService;
        this.tenantBootstrapService = tenantBootstrapService;
        this.auditLogService = auditLogService;
        this.autoApprove = autoApprove;
        this.callbackToken = callbackToken == null ? "" : callbackToken;
    }

    @Transactional
    public CertificationApplicationPayload submit(long companyId) {
        long userId = AuthContext.userId();
        accessControlService.requireLegalOrClaim(companyId);
        Company company = requireCompany(companyId);
        CompanyCertificationApplication pending = applicationMapper.selectOne(
                new LambdaQueryWrapper<CompanyCertificationApplication>()
                        .eq(CompanyCertificationApplication::getCompanyId, companyId)
                        .eq(CompanyCertificationApplication::getApplicantUserId, userId)
                        .eq(CompanyCertificationApplication::getStatus, "SUBMITTED")
                        .orderByDesc(CompanyCertificationApplication::getId)
                        .last("LIMIT 1"));
        if (pending != null) {
            return toPayload(pending, company.getName());
        }
        if ("VERIFIED".equals(company.getCertificationStatus())) {
            throw new BusinessException("企业已完成认证");
        }
        if (autoApprove && (!"VERIFIED".equals(company.getRealNameStatus())
                || !"VERIFIED".equals(company.getFaceStatus()))) {
            throw new BusinessException("请先完成实名和人脸核验");
        }

        CompanyCertificationApplication application = new CompanyCertificationApplication();
        application.setCompanyId(companyId);
        application.setApplicantUserId(userId);
        application.setProviderRequestId("CERT-" + UUID.randomUUID().toString().replace("-", ""));
        application.setStatus("SUBMITTED");
        application.setSubmittedAt(LocalDateTime.now());
        applicationMapper.insert(application);
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, companyId)
                .set(Company::getCertificationStatus, "PENDING_REVIEW"));
        auditLogService.log(companyId, "COMPANY_CERTIFICATION", application.getId(), "SUBMIT", "提交企业认证审核");

        if (autoApprove) {
            approve(application, company, "开发环境自动审核");
        }
        return toPayload(application, company.getName());
    }

    @Transactional
    public CertificationApplicationPayload submit(String companyId) {
        try {
            return submit(Long.parseLong(companyId));
        } catch (NumberFormatException e) {
            throw new BusinessException("企业 ID 格式不正确");
        }
    }

    public List<CertificationApplicationPayload> myApplications() {
        long userId = AuthContext.userId();
        return applicationMapper.selectList(new LambdaQueryWrapper<CompanyCertificationApplication>()
                        .eq(CompanyCertificationApplication::getApplicantUserId, userId)
                        .orderByDesc(CompanyCertificationApplication::getCreatedAt))
                .stream()
                .map(application -> {
                    Company company = companyMapper.selectById(application.getCompanyId());
                    return toPayload(application, company == null ? "未知企业" : company.getName());
                })
                .toList();
    }

    @Transactional
    public CertificationApplicationPayload review(String suppliedToken, CertificationReviewRequest request) {
        requireValidCallbackToken(suppliedToken);
        CompanyCertificationApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<CompanyCertificationApplication>()
                        .eq(CompanyCertificationApplication::getProviderRequestId, request.providerRequestId())
                        .last("LIMIT 1"));
        if (application == null) {
            throw new BusinessException("认证申请不存在");
        }
        Company company = requireCompany(application.getCompanyId());
        if (!"SUBMITTED".equals(application.getStatus())) {
            if (request.decision().equals(application.getStatus())) {
                return toPayload(application, company.getName());
            }
            throw new BusinessException("认证申请已完成，不能重复变更结果");
        }
        if ("APPROVED".equals(request.decision())) {
            approve(application, company, request.reason());
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BusinessException("驳回认证时必须填写原因");
            }
            reject(application, company, request.reason());
        }
        return toPayload(application, company.getName());
    }

    private void approve(CompanyCertificationApplication application, Company company, String reason) {
        long companyId = company.getId();
        int memberUpdated = companyMemberMapper.update(new LambdaUpdateWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, application.getApplicantUserId())
                .eq(CompanyMember::getRoleCode, "LEGAL_CANDIDATE")
                .eq(CompanyMember::getStatus, "PENDING")
                .set(CompanyMember::getRoleCode, "LEGAL")
                .set(CompanyMember::getIsLegalPerson, true)
                .set(CompanyMember::getIsAdministrator, false)
                .set(CompanyMember::getStatus, "ACTIVE"));
        boolean alreadyLegal = memberUpdated == 0 && companyMemberMapper.selectCount(
                new LambdaQueryWrapper<CompanyMember>()
                        .eq(CompanyMember::getCompanyId, companyId)
                        .eq(CompanyMember::getUserId, application.getApplicantUserId())
                        .eq(CompanyMember::getRoleCode, "LEGAL")
                        .eq(CompanyMember::getStatus, "ACTIVE")) == 1;
        if (memberUpdated != 1 && !alreadyLegal) {
            throw new BusinessException("候选法人状态已变化，请人工复核");
        }
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, companyId)
                .set(Company::getCertificationStatus, "VERIFIED")
                .set(Company::getRealNameStatus, "VERIFIED")
                .set(Company::getFaceStatus, "VERIFIED"));
        tenantBootstrapService.initialize(companyId, application.getApplicantUserId());
        finish(application, "APPROVED", reason);
        auditLogService.logAs(companyId, application.getApplicantUserId(), "COMPANY_CERTIFICATION",
                application.getId(), "APPROVE", safeReason(reason));
    }

    private void reject(CompanyCertificationApplication application, Company company, String reason) {
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, company.getId())
                .set(Company::getCertificationStatus, "REJECTED"));
        finish(application, "REJECTED", reason);
        auditLogService.logAs(company.getId(), application.getApplicantUserId(), "COMPANY_CERTIFICATION",
                application.getId(), "REJECT", safeReason(reason));
    }

    private void finish(CompanyCertificationApplication application, String status, String reason) {
        application.setStatus(status);
        application.setReviewReason(safeReason(reason));
        application.setReviewedAt(LocalDateTime.now());
        int updated = applicationMapper.update(new LambdaUpdateWrapper<CompanyCertificationApplication>()
                .eq(CompanyCertificationApplication::getId, application.getId())
                .eq(CompanyCertificationApplication::getStatus, "SUBMITTED")
                .set(CompanyCertificationApplication::getStatus, status)
                .set(CompanyCertificationApplication::getReviewReason, application.getReviewReason())
                .set(CompanyCertificationApplication::getReviewedAt, application.getReviewedAt()));
        if (updated != 1) {
            throw new BusinessException("认证申请状态已变化，请勿重复处理");
        }
    }

    private Company requireCompany(long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        return company;
    }

    private void requireValidCallbackToken(String suppliedToken) {
        if (callbackToken.isBlank() || suppliedToken == null || !MessageDigest.isEqual(
                callbackToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("认证回调凭证无效");
        }
    }

    private CertificationApplicationPayload toPayload(CompanyCertificationApplication application, String companyName) {
        return new CertificationApplicationPayload(
                String.valueOf(application.getId()), String.valueOf(application.getCompanyId()), companyName,
                application.getProviderRequestId(), application.getStatus(), application.getReviewReason(),
                text(application.getSubmittedAt()), text(application.getReviewedAt()));
    }

    private String safeReason(String reason) {
        return reason == null ? "" : reason.trim();
    }

    private String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
