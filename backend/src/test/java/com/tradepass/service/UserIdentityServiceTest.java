package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.Company;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserIdentityServiceTest {
    private SysUserMapper userMapper;
    private CompanyMapper companyMapper;
    private UserIdentityService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        companyMapper = mock(CompanyMapper.class);
        service = new UserIdentityService(userMapper, companyMapper);
        AuthContext.set(8L, 4L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void returnsCurrentUserNameForVerifiedCompany() {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setNickname(" 张采购 ");
        when(userMapper.selectById(8L)).thenReturn(user);
        Company company = new Company();
        company.setId(4L);
        company.setRealNameStatus("VERIFIED");
        when(companyMapper.selectById(4L)).thenReturn(company);

        assertThat(service.requireCurrentVerifiedName(4L)).isEqualTo("张采购");
    }

    @Test
    void refusesConfirmationUntilCompanyRealNameVerificationCompletes() {
        Company company = new Company();
        company.setId(4L);
        company.setRealNameStatus("PENDING");
        when(companyMapper.selectById(4L)).thenReturn(company);

        assertThatThrownBy(() -> service.requireCurrentVerifiedName(4L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前企业尚未完成实名认证，不能确认销售单");
    }
}
