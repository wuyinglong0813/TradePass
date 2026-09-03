package com.tradepass.service;

import com.tradepass.entity.SysUser;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.CounterpartyRelationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductionExperienceAccountTest {
    private final CompanyMapper companies = mock(CompanyMapper.class);
    private final CompanyMemberMapper members = mock(CompanyMemberMapper.class);
    private final CounterpartyRelationMapper relations = mock(CounterpartyRelationMapper.class);
    private final TenantBootstrapService bootstrap = mock(TenantBootstrapService.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=prod")
            .withBean(CompanyMapper.class, () -> companies)
            .withBean(CompanyMemberMapper.class, () -> members)
            .withBean(CounterpartyRelationMapper.class, () -> relations)
            .withBean(TenantBootstrapService.class, () -> bootstrap)
            .withUserConfiguration(ProductionService.class);

    @Test
    void productionDoesNotGrantPrivilegesToFormerExperiencePhones() {
        verifyDisabled(contextRunner);
    }

    @Test
    void legacyExperienceEnvironmentFlagCannotReenableProductionPrivileges() {
        verifyDisabled(contextRunner.withPropertyValues("TRADEPASS_EXPERIENCE_TEST_ACCOUNTS_ENABLED=true"));
    }

    private void verifyDisabled(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("tradepass.experience-test-accounts.enabled", Boolean.class))
                    .isFalse();
            var service = context.getBean(ExperienceTestAccountService.class);
            SysUser user = new SysUser();
            user.setId(31L);
            assertThat(service.provisionIfConfigured(user, "15632287507")).isNull();
            assertThat(service.provisionIfConfigured(user, "19802166615")).isNull();
            verifyNoInteractions(companies, members, relations, bootstrap);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ExperienceTestAccountService.class)
    static class ProductionService { }
}
