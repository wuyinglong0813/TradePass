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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FadadaCompanyVerificationTest {
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
