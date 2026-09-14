package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.config.FadadaProperties;
import com.tradepass.entity.Company;
import com.tradepass.entity.FadadaCorpIdentity;
import com.tradepass.integration.fadada.FadadaCompanyGateway;
import com.tradepass.mapper.*;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.tradepass.service.CompanyCertificationService.CertifiedApplicantRole;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FadadaCompanyVerificationTest {
    private static final List<String> SCOPES = List.of("ident_info", "seal_info", "signtask_init", "signtask_info", "signtask_file");

    @Test
    void renewingAuthorizationDoesNotTurnAnActiveCompanyBackIntoAnUnapprovedApplication() {
        var f = new Fixture();
        f.company.setCertificationStatus("VERIFIED");
        var properties = new FadadaProperties();
        properties.setEnabled(true); properties.setAppId("test-app"); properties.setAppSecret("test-secret");
        properties.setServerUrl("https://example.test"); properties.setCallbackUrl("https://example.test/callback");
        when(f.gateway.createAuthUrl(any())).thenReturn("https://example.test/auth");
        com.tradepass.common.AuthContext.set(7L, 3L);
        try {
            var service = new FadadaCompanyService(f.identities, f.seals, f.companies, mock(AccessControlService.class),
                    f.certifications, f.personal, f.gateway, properties, new ObjectMapper());
            service.createAuthUrl(3L);
            verify(f.companies, never()).update(any(Wrapper.class));
        } finally { com.tradepass.common.AuthContext.clear(); }
    }

    @ParameterizedTest
    @CsvSource({"legal_rep,LEGAL", "deputy_auth,ADMIN"})
    void assignsOnlyTheRoleProvenForTheActualApplicant(String operatorType, CertifiedApplicantRole expectedRole) {
        var f = new Fixture();
        f.detail(operatorType, "open-user-7", "identified");
        assertThat(f.service.sync(3L).status()).isEqualTo("VERIFIED");
        verify(f.certifications).completeProviderCertification(eq(3L), eq(7L), eq("FDD-CORP-local-3"), anyString(), eq(expectedRole));
        assertThat(f.identity.getOperatorType()).isEqualTo(operatorType);
        assertThat(f.identity.getOperatorId()).isEqualTo("open-user-7");
    }

    @ParameterizedTest
    @CsvSource({"legal_rep,another-user,identified", "deputy_auth,another-user,identified",
            "legal_rep,'',identified", "unknown,open-user-7,identified", "'',open-user-7,identified",
            "legal_rep,open-user-7,unidentified"})
    void incompleteOrMismatchedOperatorEvidenceNeverActivatesTheApplicant(String type, String operatorId, String status) {
        var f = new Fixture();
        f.detail(type, operatorId, status);
        var result = f.service.sync(3L);
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.failureReason()).isNotBlank();
        verifyNoInteractions(f.certifications);
        verify(f.gateway, never()).listSeals(anyString());
    }

    @ParameterizedTest
    @CsvSource({"unauthorized,true", "authorized,false"})
    void enterpriseVerificationAloneDoesNotProveAuthorization(String binding, boolean completeScopes) {
        var f = new Fixture();
        f.detail("deputy_auth", "open-user-7", "identified");
        when(f.gateway.getCompany(anyString(), any())).thenReturn(new FadadaCompanyGateway.CompanyAccount(
                "local-3", "corp-3", binding, "identified", "enable", completeScopes ? SCOPES : List.of("ident_info")));
        assertThat(f.service.sync(3L).status()).isEqualTo("IN_PROGRESS");
        verifyNoInteractions(f.certifications);
    }

    @Test
    void missingPersonalVerificationDoesNotActivateAnOtherwiseVerifiedCompany() {
        var f = new Fixture();
        f.detail("legal_rep", "open-user-7", "identified");
        when(f.personal.verifiedOpenUserId(7L)).thenThrow(new com.tradepass.common.BusinessException("个人认证尚未完成"));
        assertThat(f.service.sync(3L).status()).isEqualTo("IN_PROGRESS");
        verifyNoInteractions(f.certifications);
    }

    @Test
    void mismatchedCompanyIdentityCannotGrantRoles() {
        var f = new Fixture();
        when(f.gateway.getIdentity("corp-3")).thenReturn(new FadadaCompanyGateway.CompanyIdentity(
                "corp-3", "identified", "另一个企业", "OTHER-CREDIT", "张三", "legalRep", null, null, "legal_rep", "open-user-7"));
        assertThatThrownBy(() -> f.service.sync(3L)).hasMessageContaining("企业名称或统一社会信用代码不一致");
        verifyNoInteractions(f.certifications);
    }

    private static class Fixture {
        final FadadaCorpIdentityMapper identities = mock(FadadaCorpIdentityMapper.class);
        final FadadaCorpSealMapper seals = mock(FadadaCorpSealMapper.class);
        final CompanyMapper companies = mock(CompanyMapper.class);
        final CompanyCertificationService certifications = mock(CompanyCertificationService.class);
        final FadadaPersonalIdentityService personal = mock(FadadaPersonalIdentityService.class);
        final FadadaCompanyGateway gateway = mock(FadadaCompanyGateway.class);
        final FadadaCorpIdentity identity = new FadadaCorpIdentity();
        final Company company = new Company();
        final FadadaCompanyService service;
        Fixture() {
            MybatisTestSupport.initialize(Company.class, FadadaCorpIdentity.class, com.tradepass.entity.FadadaCorpSeal.class);
            company.setId(3L); company.setName("认证企业");
            company.setCreditCode("TEST-CREDIT"); company.setCertificationStatus("PENDING_REVIEW");
            identity.setId(5L); identity.setCompanyId(3L); identity.setApplicantUserId(7L);
            identity.setClientCorpId("local-3"); identity.setLocalStatus("IN_PROGRESS");
            when(identities.selectOne(any(Wrapper.class))).thenReturn(identity);
            when(companies.selectByIdForUpdate(3L)).thenReturn(company);
            when(personal.verifiedOpenUserId(7L)).thenReturn("open-user-7");
            when(gateway.getCompany(anyString(), any())).thenReturn(new FadadaCompanyGateway.CompanyAccount(
                    "local-3", "corp-3", "authorized", "identified", "enable", SCOPES));
            service = new FadadaCompanyService(identities, seals, companies, mock(AccessControlService.class),
                    certifications, personal, gateway, new FadadaProperties(), new ObjectMapper());
        }
        void detail(String type, String operatorId, String status) {
            when(gateway.getIdentity("corp-3")).thenReturn(new FadadaCompanyGateway.CompanyIdentity(
                    "corp-3", status, "认证企业", "TEST-CREDIT", "张三", "letter", null, null, type, operatorId));
        }
    }

    @Test void cachedProviderCertificationCannotAuthorizeChangedOrUnverifiedCompany() {
        MybatisTestSupport.initialize(FadadaCorpIdentity.class);
        var identities = mock(FadadaCorpIdentityMapper.class);
        var companies = mock(CompanyMapper.class);
        var service = new FadadaCompanyService(identities, mock(FadadaCorpSealMapper.class), companies,
                mock(AccessControlService.class), mock(CompanyCertificationService.class),
                mock(FadadaPersonalIdentityService.class), mock(FadadaCompanyGateway.class),
                new FadadaProperties(), new ObjectMapper());
        Company company = new Company(); company.setId(3L); company.setName("认证企业");
        company.setCreditCode("TEST-CREDIT"); company.setCertificationStatus("VERIFIED");
        FadadaCorpIdentity identity = new FadadaCorpIdentity(); identity.setLocalStatus("VERIFIED");
        identity.setOpenCorpId("corp-3"); identity.setVerifiedName("认证企业"); identity.setVerifiedCreditCode("TEST-CREDIT");
        identity.setAuthScopes("[\"ident_info\",\"seal_info\",\"signtask_init\",\"signtask_info\",\"signtask_file\"]");
        when(companies.selectById(3L)).thenReturn(company);
        when(identities.selectOne(any(Wrapper.class))).thenReturn(identity);
        assertThat(service.requireVerified(3L)).isSameAs(identity);
        company.setName("篡改名称");
        assertThatThrownBy(() -> service.requireVerified(3L)).hasMessageContaining("认证记录不一致");
        company.setName("认证企业"); company.setCertificationStatus("REJECTED");
        assertThatThrownBy(() -> service.requireVerified(3L)).hasMessage("请先完成企业认证");
    }
}
