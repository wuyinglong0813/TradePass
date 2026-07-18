package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.RoleDef;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.RoleDefMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessControlServiceTest {
    private CompanyMemberMapper memberMapper;
    private RoleDefMapper roleMapper;
    private AccessControlService service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(CompanyMemberMapper.class);
        roleMapper = mock(RoleDefMapper.class);
        service = new AccessControlService(memberMapper, roleMapper,
                new RolePermissionService(), new ObjectMapper());
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesContextOrVerifiedRequestedCompany() {
        assertThat(service.resolveCompanyId(null)).isEqualTo(3L);
        assertThat(service.resolveCompanyId(" ")).isEqualTo(3L);

        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThat(service.resolveCompanyId("9")).isEqualTo(9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsMalformedAndUnauthorizedCompanySelection() {
        assertThatThrownBy(() -> service.resolveCompanyId("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("企业 ID 格式不正确");

        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertThatThrownBy(() -> service.resolveCompanyId("9"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("你不是该企业的有效成员");
    }

    @Test
    @SuppressWarnings("unchecked")
    void combinesBuiltInAndCustomPermissions() {
        CompanyMember member = activeMember("FINANCE", "[\"order_create\"]");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThat(service.hasPermission(3L, "invoice_view")).isTrue();
        assertThat(service.hasPermission(3L, "order_create")).isTrue();
        assertThat(service.hasPermission(3L, "member_manage")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistedRoleCanGrantAllPermissions() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(activeMember("CUSTOM", null));
        RoleDef role = new RoleDef();
        role.setPermissions("[\"all\"]");
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);

        assertThat(service.hasPermission(3L, "anything")).isTrue();
        assertThatCode(() -> service.requirePermission(3L, "anything")).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniesMissingMemberAndMalformedPermissionJson() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(service.hasPermission(3L, "order_view")).isFalse();
        assertThatThrownBy(() -> service.requireAnyPermission(3L, "a", "b"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权执行该操作");

        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(activeMember("FINANCE", "not-json"));
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.hasPermission(3L, "invoice_view"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("权限配置格式错误");
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforcesLegalAndClaimBoundaries() {
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 1L, 0L, 1L);
        assertThatThrownBy(() -> service.requireLegal(3L)).hasMessage("无权操作：仅法人可执行");
        assertThatCode(() -> service.requireLegal(3L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireLegalOrClaim(3L)).hasMessage("无权执行企业认领认证");
        assertThatCode(() -> service.requireLegalOrClaim(3L)).doesNotThrowAnyException();
    }

    private CompanyMember activeMember(String role, String customPermissions) {
        CompanyMember member = new CompanyMember();
        member.setRoleCode(role);
        member.setStatus("ACTIVE");
        member.setCustomPermissions(customPermissions);
        return member;
    }
}
