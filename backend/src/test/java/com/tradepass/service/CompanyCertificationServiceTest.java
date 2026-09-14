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
import com.tradepass.service.CompanyCertificationService.CertifiedApplicantRole;

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
        when(companyMapper.selectByIdForUpdate(3L)).thenReturn(company);
        CompanyCertificationService service = service(true, "");

        CertificationApplicationPayload payload = service.submit(3L);

        assertThat(payload.status()).isEqualTo("APPROVED");
        assertThat(payload.providerRequestId()).startsWith("MOCK-CA-");
        verify(accessControlService).requireLegalOrClaim(3L);
        verify(tenantBootstrapService).initialize(3L, 7L);
        verify(auditLogService).logAs(3L, 7L, "COMPANY_CERTIFICATION", 18L,
                "APPROVE", "体验测试模拟认证自动审核");
    }

    @Test
    void productionCallbackIsSecretProtectedAndIdempotent() {
        Company company = company();
        when(companyMapper.selectByIdForUpdate(3L)).thenReturn(company);
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
        assertThatThrownBy(() -> service.review("callback-secret", approved))
                .hasMessageContaining("核验经办人身份");
        service.completeProviderCertification(3L, 7L, "provider-18", "核验通过", CertifiedApplicantRole.ADMIN);
        assertThat(service.review("callback-secret", approved).status()).isEqualTo("APPROVED");
        verify(tenantBootstrapService).initialize(3L, 7L);
    }

    @Test
    void authorizedAgentBecomesAdministratorWithoutLegalFlagOrInheritedAllPermissions() {
        assertAssignedRole(CertifiedApplicantRole.ADMIN, false, true);
    }

    @Test
    void verifiedLegalOperatorBecomesLegalRepresentative() {
        assertAssignedRole(CertifiedApplicantRole.LEGAL, true, false);
    }

    private void assertAssignedRole(CertifiedApplicantRole role, boolean legal, boolean administrator) {
        when(companyMapper.selectByIdForUpdate(3L)).thenReturn(company());
        when(memberMapper.update(any(Wrapper.class))).thenAnswer(invocation -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CompanyMember>) invocation.getArgument(0);
            assertThat(update.getSqlSegment()).contains("role_code", "status");
            assertThat(update.getSqlSet()).contains("role_code=", "role_codes=", "is_legal_person=", "is_administrator=", "custom_permissions=");
            var values = update.getParamNameValuePairs();
            for (String assignment : update.getSqlSet().split(",")) {
                String key = assignment.substring(assignment.indexOf("MPGENVAL"), assignment.indexOf('}'));
                Object value = values.get(key);
                if (assignment.startsWith("role_code=")) assertThat(value).isEqualTo(role.name());
                if (assignment.startsWith("role_codes=")) assertThat(value).isEqualTo("[\"" + role.name() + "\"]");
                if (assignment.startsWith("is_legal_person=")) assertThat(value).isEqualTo(legal);
                if (assignment.startsWith("is_administrator=")) assertThat(value).isEqualTo(administrator);
                if (assignment.startsWith("custom_permissions=")) assertThat(value).isNull();
            }
            return 1;
        });
        service(false, "").completeProviderCertification(3L, 7L, "corp-3", "已核验", role);
        verify(tenantBootstrapService).initialize(3L, 7L);
    }

    @Test
    void missingOperatorRoleCannotActivateAnyMembership() {
        assertThatThrownBy(() -> service(false, "").completeProviderCertification(3L, 7L, "corp-3", "", null))
                .hasMessageContaining("身份尚未确认");
        org.mockito.Mockito.verifyNoInteractions(companyMapper, memberMapper, tenantBootstrapService);
    }

    @Test
    void acceptsLegacyCompanyWhoseApplicantIsAlreadyTheActiveLegalMember() {
        Company company = company();
        company.setRealNameStatus("VERIFIED");
        company.setFaceStatus("VERIFIED");
        when(companyMapper.selectByIdForUpdate(3L)).thenReturn(company);
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
