package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.LoginSession;
import com.tradepass.common.TradePassDtos.MePayload;
import com.tradepass.common.TradePassDtos.UserProfile;
import com.tradepass.dto.request.BindCompanyRequest;
import com.tradepass.dto.request.BindPhoneRequest;
import com.tradepass.dto.request.SwitchCompanyRequest;
import com.tradepass.dto.request.SwitchUserRequest;
import com.tradepass.dto.request.WechatLoginRequest;
import com.tradepass.dto.response.TodoItem;
import com.tradepass.entity.Company;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.PermDef;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.PermDefMapper;
import com.tradepass.mapper.SysUserMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private SysUserMapper userMapper;
    private CompanyMapper companyMapper;
    private CompanyMemberMapper memberMapper;
    private PermDefMapper permissionMapper;
    private TradeContractMapper contractMapper;
    private WechatService wechatService;
    private AccessControlService accessControlService;
    private AuthSessionService sessionService;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(SysUser.class, CompanyMember.class, PermDef.class);
        userMapper = mock(SysUserMapper.class);
        companyMapper = mock(CompanyMapper.class);
        memberMapper = mock(CompanyMemberMapper.class);
        permissionMapper = mock(PermDefMapper.class);
        contractMapper = mock(TradeContractMapper.class);
        wechatService = mock(WechatService.class);
        accessControlService = mock(AccessControlService.class);
        sessionService = mock(AuthSessionService.class);
        when(accessControlService.effectiveRole(anyLong(), anyLong()))
                .thenReturn(new AccessControlService.EffectiveRole("ADMIN", "管理员", List.of("member_manage")));
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void productionLoginRejectsUnverifiedRawPhone() {
        AuthService service = service(false);
        when(wechatService.resolveOpenid("code")).thenReturn("openid");

        assertThatThrownBy(() -> service.wechatLogin(
                new WechatLoginRequest("code", "昵称", null, "13800000000", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("生产环境不接受未经验证的手机号");
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsGuestUserAndIssuesRandomSession() {
        AuthService service = service(true);
        when(wechatService.resolveOpenid("dev-new")).thenReturn("dev-new");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(21L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
        when(sessionService.issue(21L)).thenReturn("session-token");

        LoginSession result = service.wechatLogin(
                new WechatLoginRequest("dev-new", "新用户", null, "13800000000", null));

        assertThat(result.token()).isEqualTo("session-token");
        assertThat(result.user().id()).isEqualTo("21");
        assertThat(result.user().currentRole()).isEqualTo("GUEST");
        assertThat(result.user().phone()).isEqualTo("13800000000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesExistingUserAndRestoresCompanyRole() {
        AuthService service = service(true);
        when(wechatService.resolveOpenid("dev-phone-login")).thenReturn("dev-phone-login");
        SysUser user = new SysUser();
        user.setId(7L);
        user.setOpenid("old-openid");
        user.setNickname("旧昵称");
        user.setPhone("13800000000");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(memberMapper.selectUserCompanies(7L)).thenReturn(List.of(
                Map.of("companyId", 3L, "companyName", "测试企业", "roleCode", "ADMIN")));
        when(memberMapper.selectMemberInfoAnyCompany(7L)).thenReturn(memberRow(7L, "ADMIN", "ACTIVE"));
        when(sessionService.issue(7L)).thenReturn("token");

        LoginSession result = service.wechatLogin(
                new WechatLoginRequest("dev-phone-login", "新昵称", null, "13800000000", null));

        assertThat(result.user().nickname()).isEqualTo("新昵称");
        assertThat(result.user().currentCompanyId()).isEqualTo("3");
        assertThat(result.user().currentRole()).isEqualTo("ADMIN");
        verify(userMapper).updateById(user);
    }

    @Test
    void bindsPhoneOnlyInDevelopmentAndReturnsMemberProfile() {
        AuthContext.set(7L, 3L);
        assertThatThrownBy(() -> service(false).bindPhone(new BindPhoneRequest("13800000000")))
                .hasMessage("请使用微信手机号验证完成绑定");

        AuthService devService = service(true);
        when(memberMapper.selectMemberInfo(7L, 3L)).thenReturn(memberRow(7L, "FINANCE", "ACTIVE"));
        UserProfile profile = devService.bindPhone(new BindPhoneRequest("13800000000"));

        assertThat(profile.currentCompanyId()).isEqualTo("3");
        assertThat(profile.currentRole()).isEqualTo("FINANCE");
        verify(userMapper).update(any(Wrapper.class));
    }

    @Test
    void buildsManagerTodosAndReturnsNoneOutsideCompanyOrRole() {
        AuthService service = service(true);
        AuthContext.set(7L, null);
        assertThat(service.myTodos()).isEmpty();

        AuthContext.set(7L, 3L);
        when(accessControlService.hasPermission(3L, "member_manage")).thenReturn(false);
        when(accessControlService.hasPermission(3L, "auth_manage")).thenReturn(false);
        assertThat(service.myTodos()).isEmpty();

        when(accessControlService.hasPermission(3L, "member_manage")).thenReturn(true);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        Company company = company(3L, "当前企业", 7L);
        company.setCertificationStatus("PENDING_REVIEW");
        when(companyMapper.selectById(3L)).thenReturn(company);
        when(contractMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        List<TodoItem> todos = service.myTodos();
        assertThat(todos).extracting(TodoItem::type)
                .containsExactly("APPROVAL", "CERT", "CONTRACT");
        assertThat(todos).extracting(TodoItem::count).containsExactly(2, 1, 3);
    }

    @Test
    void preventsSwitchingIntoAnotherTenant() {
        AuthContext.set(7L, 3L);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service(true).switchCompany(new SwitchCompanyRequest("9")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("你不是该公司成员");
        assertThatThrownBy(() -> service(true).switchCompany(new SwitchCompanyRequest("bad")))
                .hasMessage("ID 格式不正确");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bindsOnlyMatchingCompanyOwnedByCurrentUser() {
        AuthContext.set(7L, null);
        AuthService service = service(true);
        BindCompanyRequest request = new BindCompanyRequest("3", "测试企业", "CODE3", "法人",
                null, null, null, null);
        when(companyMapper.selectById(3L)).thenReturn(null);
        assertThatThrownBy(() -> service.bindCompany(request)).hasMessage("企业不存在，请先提交企业资料");

        Company company = company(3L, "另一名称", 7L);
        when(companyMapper.selectById(3L)).thenReturn(company);
        assertThatThrownBy(() -> service.bindCompany(request)).hasMessage("企业资料与已提交记录不一致");

        company.setName("测试企业");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(memberMapper.selectUserCompanies(7L)).thenReturn(List.of());
        when(memberMapper.selectMemberInfo(7L, 3L)).thenReturn(memberRow(7L, "LEGAL_CANDIDATE", "PENDING"));

        MePayload result = service.bindCompany(request);
        assertThat(result.member().roleCode()).isEqualTo("LEGAL_CANDIDATE");
        verify(memberMapper).insert(any(CompanyMember.class));
    }

    @Test
    void supportsFallbackProfileDevUsersLogoutAndUserSwitch() {
        AuthService service = service(true);
        AuthContext.set(7L, null);
        when(memberMapper.selectUserCompanies(7L)).thenReturn(List.of());
        when(memberMapper.selectMemberInfo(7L, 0L)).thenReturn(null);
        SysUser user = new SysUser();
        user.setNickname("访客");
        user.setPhone("13800000000");
        when(userMapper.selectById(7L)).thenReturn(user);
        assertThat(service.me().user().currentRole()).isEqualTo("GUEST");
        assertThat(service.me().company().name()).isEqualTo("未加入企业");

        when(memberMapper.selectDevUsers()).thenReturn(List.of(
                Map.of("id", 7L, "nickname", "管理员", "phone", "13800000000", "roleCode", "ADMIN")));
        assertThat(service.listDevUsers().get(0).roleText()).isEqualTo("管理员");

        when(memberMapper.selectMemberInfoAnyCompany(7L)).thenReturn(memberRow(7L, "ADMIN", "ACTIVE"));
        when(sessionService.issue(7L)).thenReturn("switched-token");
        LoginSession switched = service.switchUser(new SwitchUserRequest("7"));
        assertThat(switched.token()).isEqualTo("switched-token");

        service.logout("Bearer switched-token");
        verify(sessionService).revoke("Bearer switched-token");
    }

    private AuthService service(boolean devEnabled) {
        return new AuthService(userMapper, companyMapper, memberMapper, permissionMapper, contractMapper,
                wechatService, new RolePermissionService(), accessControlService, sessionService, devEnabled);
    }

    private Map<String, Object> memberRow(long userId, String role, String status) {
        return Map.of(
                "userId", userId,
                "nickname", "测试用户",
                "phone", "13800000000",
                "roleCode", role,
                "status", status
        );
    }

    private Company company(long id, String name, long createdBy) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setCreditCode("CODE3");
        company.setLegalPersonName("法人");
        company.setCreatedBy(createdBy);
        company.setCertificationStatus("VERIFIED");
        company.setRealNameStatus("VERIFIED");
        company.setFaceStatus("VERIFIED");
        company.setSealStatus("UPLOADED");
        return company;
    }
}
