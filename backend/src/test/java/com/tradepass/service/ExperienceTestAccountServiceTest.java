package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tradepass.entity.Company;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.CounterpartyRelationEntity;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperienceTestAccountServiceTest {
    private CompanyMapper companyMapper;
    private CompanyMemberMapper companyMemberMapper;
    private CounterpartyRelationMapper relationMapper;
    private TenantBootstrapService tenantBootstrapService;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(Company.class, CompanyMember.class, CounterpartyRelationEntity.class);
        companyMapper = mock(CompanyMapper.class);
        companyMemberMapper = mock(CompanyMemberMapper.class);
        relationMapper = mock(CounterpartyRelationMapper.class);
        tenantBootstrapService = mock(TenantBootstrapService.class);
    }

    @Test
    void ignoresAccountsWhenDisabledOrPhoneIsNotConfigured() {
        SysUser user = user(31L);

        assertThat(service(false).provisionIfConfigured(user, "15632287507")).isNull();
        assertThat(service(true).provisionIfConfigured(user, "13900000000")).isNull();

        verify(companyMapper, never()).selectOne(any(Wrapper.class));
        verify(companyMemberMapper, never()).insert(any(CompanyMember.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void provisionsHebeiAccountWithLegalAccessTemplatesAndCounterparty() {
        when(companyMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId("91130100MA00000001".equals(company.getCreditCode()) ? 101L : 202L);
            return 1;
        }).when(companyMapper).insert(any(Company.class));
        when(companyMemberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        Long assignedCompanyId = service(true).provisionIfConfigured(user(31L), "15632287507");

        assertThat(assignedCompanyId).isEqualTo(101L);
        ArgumentCaptor<CompanyMember> memberCaptor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberMapper).insert(memberCaptor.capture());
        CompanyMember member = memberCaptor.getValue();
        assertThat(member.getCompanyId()).isEqualTo(101L);
        assertThat(member.getUserId()).isEqualTo(31L);
        assertThat(member.getRoleCode()).isEqualTo("LEGAL");
        assertThat(member.getStatus()).isEqualTo("ACTIVE");
        assertThat(member.getIsLegalPerson()).isTrue();

        verify(tenantBootstrapService).initialize(101L, 31L);
        verify(tenantBootstrapService).initialize(202L, 31L);

        ArgumentCaptor<CounterpartyRelationEntity> relationCaptor =
                ArgumentCaptor.forClass(CounterpartyRelationEntity.class);
        verify(relationMapper, times(2)).insert(relationCaptor.capture());
        List<CounterpartyRelationEntity> relations = relationCaptor.getAllValues();
        assertThat(relations).extracting(CounterpartyRelationEntity::getCompanyId)
                .containsExactly(101L, 202L);
        assertThat(relations).extracting(CounterpartyRelationEntity::getCounterpartyCompanyId)
                .containsExactly(202L, 101L);
        assertThat(relations).extracting(CounterpartyRelationEntity::getStatus)
                .containsOnly("ACTIVE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignsSecondPhoneToShanghaiCompany() {
        Company hebei = company(101L, "河北光屿行贸易有限公司", "91130100MA00000001");
        Company shanghai = company(202L, "上海远航进出口有限公司", "91310000MA00000002");
        when(companyMapper.selectOne(any(Wrapper.class))).thenReturn(hebei, shanghai);
        when(companyMemberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(relationMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        Long assignedCompanyId = service(true).provisionIfConfigured(user(41L), "19802166615");

        assertThat(assignedCompanyId).isEqualTo(202L);
        ArgumentCaptor<CompanyMember> memberCaptor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getCompanyId()).isEqualTo(202L);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(41L);
        assertThat(memberCaptor.getValue().getRoleCode()).isEqualTo("LEGAL");
    }

    private ExperienceTestAccountService service(boolean enabled) {
        return new ExperienceTestAccountService(companyMapper, companyMemberMapper, relationMapper,
                tenantBootstrapService, enabled);
    }

    private SysUser user(long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private Company company(long id, String name, String creditCode) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setCreditCode(creditCode);
        return company;
    }
}
