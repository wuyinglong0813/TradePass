package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyCertificationServiceTest {
    private CompanyMapper companyMapper;
    private CompanyMemberMapper memberMapper;
    private CompanyCertificationApplicationMapper applicationMapper;
    private AccessControlService accessControlService;
    private TenantBootstrapService tenantBootstrapService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(Company.class, CompanyMember.class, CompanyCertificationApplication.class);
        companyMapper = mock(CompanyMapper.class);
        memberMapper = mock(CompanyMemberMapper.class);
        applicationMapper = mock(CompanyCertificationApplicationMapper.class);
        accessControlService = mock(AccessControlService.class);
        tenantBootstrapService = mock(TenantBootstrapService.class);
        auditLogService = mock(AuditLogService.class);
        when(memberMapper.update(any(Wrapper.class))).thenReturn(1);
        when(applicationMapper.update(any(Wrapper.class))).thenReturn(1);
        doAnswer(invocation -> {
            CompanyCertificationApplication application = invocation.getArgument(0);
            application.setId(18L);
            return 1;
        }).when(applicationMapper).insert(any(CompanyCertificationApplication.class));
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void developmentSubmissionCompletesOnboardingAndInitializesTenant() {
        Company company = company();
        company.setRealNameStatus("VERIFIED");
        company.setFaceStatus("VERIFIED");
        when(companyMapper.selectById(3L)).thenReturn(company);
        CompanyCertificationService service = service(true, "");

        CertificationApplicationPayload payload = service.submit(3L);

        assertThat(payload.status()).isEqualTo("APPROVED");
        assertThat(payload.providerRequestId()).startsWith("CERT-");
        verify(accessControlService).requireLegalOrClaim(3L);
        verify(tenantBootstrapService).initialize(3L, 7L);
        verify(auditLogService).logAs(3L, 7L, "COMPANY_CERTIFICATION", 18L,
                "APPROVE", "开发环境自动审核");
    }

    @Test
    void productionCallbackIsSecretProtectedAndIdempotent() {
        Company company = company();
        when(companyMapper.selectById(3L)).thenReturn(company);
        CompanyCertificationApplication application = new CompanyCertificationApplication();
        application.setId(18L);
        application.setCompanyId(3L);
        application.setApplicantUserId(7L);
        application.setProviderRequestId("provider-18");
        application.setStatus("SUBMITTED");
        when(applicationMapper.selectOne(any(Wrapper.class))).thenReturn(application);
        CompanyCertificationService service = service(false, "callback-secret");
        CertificationReviewRequest approved = new CertificationReviewRequest("provider-18", "APPROVED", "核验通过");

        assertThatThrownBy(() -> service.review("bad", approved))
                .isInstanceOf(BusinessException.class).hasMessage("认证回调凭证无效");
        assertThat(service.review("callback-secret", approved).status()).isEqualTo("APPROVED");
        assertThat(service.review("callback-secret", approved).status()).isEqualTo("APPROVED");
        verify(tenantBootstrapService).initialize(3L, 7L);
    }

    @Test
    void acceptsLegacyCompanyWhoseApplicantIsAlreadyTheActiveLegalMember() {
        Company company = company();
        company.setRealNameStatus("VERIFIED");
        company.setFaceStatus("VERIFIED");
        when(companyMapper.selectById(3L)).thenReturn(company);
        when(memberMapper.update(any(Wrapper.class))).thenReturn(0);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThat(service(true, "").submit(3L).status()).isEqualTo("APPROVED");
        verify(tenantBootstrapService).initialize(3L, 7L);
    }

    private CompanyCertificationService service(boolean autoApprove, String token) {
        return new CompanyCertificationService(companyMapper, memberMapper, applicationMapper,
                accessControlService, tenantBootstrapService, auditLogService, autoApprove, token);
    }

    private Company company() {
        Company company = new Company();
        company.setId(3L);
        company.setName("测试企业");
        company.setCertificationStatus("PENDING");
        company.setRealNameStatus("NOT_STARTED");
        company.setFaceStatus("NOT_STARTED");
        return company;
    }
}
