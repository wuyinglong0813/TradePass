package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.AuthorizationRecord;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.CompanySearchSummary;
import com.tradepass.dto.request.ApproveRequest;
import com.tradepass.dto.request.CompanySubmitRequest;
import com.tradepass.dto.request.InviteRequest;
import com.tradepass.dto.request.JoinRequest;
import com.tradepass.dto.request.RoleRequest;
import com.tradepass.dto.request.SealRequest;
import com.tradepass.dto.request.VerificationRequest;
import com.tradepass.dto.response.InviteResult;
import com.tradepass.dto.response.JoinResult;
import com.tradepass.entity.Company;
import com.tradepass.entity.CompanyInvite;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.CounterpartyRelationEntity;
import com.tradepass.entity.RoleDef;
import com.tradepass.mapper.CompanyInviteMapper;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.mapper.RoleDefMapper;
import com.tradepass.mapper.PermDefMapper;
import com.tradepass.entity.PermDef;
import com.tradepass.dto.response.RolePayload;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyServiceTest {
    private CompanyMapper companyMapper;
    private CompanyMemberMapper memberMapper;
    private CompanyInviteMapper inviteMapper;
    private CounterpartyRelationMapper relationMapper;
    private RoleDefMapper roleMapper;
    private PermDefMapper permMapper;
    private AccessControlService accessControl;
    private CompanySearchRateLimiter searchRateLimiter;
    private AuditLogService auditLogService;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(Company.class, CompanyMember.class, RoleDef.class);
        companyMapper = mock(CompanyMapper.class);
        memberMapper = mock(CompanyMemberMapper.class);
        inviteMapper = mock(CompanyInviteMapper.class);
        relationMapper = mock(CounterpartyRelationMapper.class);
        roleMapper = mock(RoleDefMapper.class);
        permMapper = mock(PermDefMapper.class);
        accessControl = mock(AccessControlService.class);
        searchRateLimiter = mock(CompanySearchRateLimiter.class);
        auditLogService = mock(AuditLogService.class);
        service = new CompanyService(companyMapper, memberMapper, inviteMapper, relationMapper,
                roleMapper, permMapper, accessControl, searchRateLimiter, new RolePermissionService(),
                auditLogService, true);
        when(accessControl.requireCompanyProfileAccess(anyLong()))
                .thenReturn(AccessControlService.CompanyProfileAccess.SENSITIVE_OWNER);
        when(accessControl.hasPermission(anyLong(), any())).thenReturn(true);
        when(accessControl.effectiveRole(anyLong(), anyLong()))
                .thenReturn(new AccessControlService.EffectiveRole("ADMIN", "管理员", List.of("member_manage")));
        when(permMapper.selectById(any())).thenAnswer(invocation -> {
            PermDef permission = new PermDef();
            permission.setCode(invocation.getArgument(0));
            return permission;
        });
        when(memberMapper.update(any(Wrapper.class))).thenReturn(1);
        when(memberMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(roleMapper.update(any(Wrapper.class))).thenReturn(1);
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void searchesPersistedCompaniesWithoutInventingFallbackData() {
        assertThat(service.searchCompanies("  ")).isEmpty();
        Company persisted = company(2L, "河北通瑞贸易有限公司");
        persisted.setCreditCode("91130100MA01234567");
        persisted.setContactPhone("13800000000");
        persisted.setBankAccount("6222021234567890123");
        when(companyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(persisted));
        assertThat(service.searchCompanies("通瑞"))
                .extracting(CompanySearchSummary::name)
                .containsExactly("河北通瑞贸易有限公司");
        assertThat(service.searchCompanies("通瑞").get(0))
                .satisfies(summary -> {
                    assertThat(summary.maskedCreditCode()).isEqualTo("9113**********4567");
                    assertThat(summary.verified()).isTrue();
                });

        when(companyMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThat(service.searchCompanies("全新企业")).isEmpty();

        assertThatThrownBy(() -> service.searchCompanies("企"))
                .hasMessage("搜索关键词至少需要 2 个字符");
        assertThatThrownBy(() -> service.searchCompanies("%%"))
                .hasMessage("搜索关键词格式不正确");
    }

    @Test
    void returnsCompanyOrExplainsMissingCompany() {
        Company company = company(3L, "当前企业");
        when(companyMapper.selectById(3L)).thenReturn(company);
        assertThat(service.getCompany("3").name()).isEqualTo("当前企业");

        assertThatThrownBy(() -> service.getCompany("99"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("企业不存在");
    }

    @Test
    void masksCounterpartySensitiveFields() {
        Company company = company(9L, "合作企业");
        company.setContactPhone("13800001234");
        company.setBankName("测试银行");
        company.setBankAccount("6222021234567890123");
        when(companyMapper.selectById(9L)).thenReturn(company);
        when(accessControl.requireCompanyProfileAccess(9L))
                .thenReturn(AccessControlService.CompanyProfileAccess.COUNTERPARTY);

        CompanyProfile profile = service.getCompany("9");

        assertThat(profile.contactPhone()).isEqualTo("138****1234");
        assertThat(profile.bankAccount()).isEqualTo("***************0123");
        assertThat(profile.realNameStatus()).isNull();
        assertThat(profile.faceStatus()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitsNewAndExistingCompanyProfiles() {
        AtomicReference<Company> stored = new AtomicReference<>();
        when(companyMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(companyMapper.selectById(any())).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            Company inserted = invocation.getArgument(0);
            inserted.setId(10L);
            stored.set(inserted);
            return 1;
        }).when(companyMapper).insert(any(Company.class));

        CompanyProfile created = service.submitCompany(
                new CompanySubmitRequest("10", "新企业", "NEW-CODE", "新法人"));
        assertThat(created.id()).isEqualTo("10");
        assertThat(created.certificationStatus()).isEqualTo("PENDING");
        verify(companyMapper).insert(any(Company.class));

        Company existing = company(11L, "旧名称");
        existing.setCreatedBy(7L);
        when(companyMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(companyMapper.selectById(11L)).thenReturn(existing);
        CompanyProfile updated = service.submitCompany(
                new CompanySubmitRequest("11", "新名称", existing.getCreditCode(), "新法人"));
        assertThat(updated.name()).isEqualTo("新名称");
        verify(companyMapper).updateById(existing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesToExposeExistingCompanyThroughSubmission() {
        Company existing = company(11L, "已入驻企业");
        existing.setCreatedBy(99L);
        existing.setBankAccount("6222021234567890123");
        when(companyMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service.submitCompany(
                new CompanySubmitRequest("11", "已入驻企业", existing.getCreditCode(), "法人")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("企业已入驻，请通过企业邀请或认领流程加入");
    }

    @Test
    void drivesCertificationAndSealStatusCommands() {
        Company company = company(3L, "当前企业");
        when(companyMapper.selectById(3L)).thenReturn(company);

        assertThat(service.submitCertification("3").id()).isEqualTo("3");
        assertThat(service.verifyRealName(new VerificationRequest("3")).name()).isEqualTo("当前企业");
        assertThat(service.verifyFace(new VerificationRequest("3")).name()).isEqualTo("当前企业");
        assertThat(service.uploadSeal(new SealRequest("3", "https://files/seal.png", "公章")))
                .satisfies(seal -> {
                    assertThat(seal.id()).isNotBlank();
                    assertThat(seal.companyId()).isEqualTo("3");
                    assertThat(seal.status()).isEqualTo("PENDING_REVIEW");
                });
        verify(companyMapper, org.mockito.Mockito.times(4)).update(any(Wrapper.class));
    }

    @Test
    void refusesSimulatedVerificationWhenProviderIsDisabled() {
        CompanyService productionService = new CompanyService(companyMapper, memberMapper, inviteMapper, relationMapper,
                roleMapper, permMapper, accessControl, searchRateLimiter, new RolePermissionService(),
                auditLogService, false);

        assertThatThrownBy(() -> productionService.verifyRealName(new VerificationRequest("3")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("认证服务尚未配置，无法完成该操作");
        assertThatThrownBy(() -> productionService.uploadSeal(new SealRequest("3", "dev://seal", "公章")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("认证服务尚未配置，无法完成该操作");
    }

    @Test
    void createsSingleUseMemberAndCounterpartyInvites() {
        InviteResult memberInvite = service.createInvite(new InviteRequest("3", null));
        assertThat(memberInvite.code()).hasSize(8);
        verify(accessControl).requireManager(3L);

        InviteResult counterpartyInvite = service.createCounterpartyInvite(new InviteRequest("3", "supplier"));
        assertThat(counterpartyInvite.code()).hasSize(8);
        verify(accessControl).requireLegal(3L);

        ArgumentCaptor<CompanyInvite> captor = ArgumentCaptor.forClass(CompanyInvite.class);
        verify(inviteMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(CompanyInvite::getType)
                .containsExactly("member", "counterparty");
        assertThat(captor.getAllValues()).allSatisfy(invite -> {
            assertThat(invite.getUsed()).isFalse();
            assertThat(invite.getExpiresAt()).isAfter(LocalDateTime.now());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsInvalidUsedAndExpiredInviteCodes() {
        when(inviteMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.joinCompany(new JoinRequest("bad"))).hasMessage("邀请码无效");

        CompanyInvite used = invite(false);
        used.setUsed(true);
        when(inviteMapper.selectOne(any(Wrapper.class))).thenReturn(used);
        assertThatThrownBy(() -> service.joinCompany(new JoinRequest("used"))).hasMessage("邀请码已被使用");

        CompanyInvite expired = invite(false);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(inviteMapper.selectOne(any(Wrapper.class))).thenReturn(expired);
        assertThatThrownBy(() -> service.joinCompany(new JoinRequest("expired"))).hasMessage("邀请码已过期");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitsMemberJoinForApprovalAndConsumesInvite() {
        CompanyInvite invite = invite(false);
        when(inviteMapper.selectOne(any(Wrapper.class))).thenReturn(invite);
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        JoinResult result = service.joinCompany(new JoinRequest("JOIN1234"));

        assertThat(result.status()).isEqualTo("PENDING");
        ArgumentCaptor<CompanyMember> memberCaptor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getCompanyId()).isEqualTo(3L);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(memberCaptor.getValue().getRoleCode()).isEqualTo("GUEST");
        assertThat(invite.getUsed()).isTrue();
        assertThat(invite.getUsedBy()).isEqualTo(7L);
        verify(inviteMapper).updateById(invite);
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotDuplicateExistingMembershipApplications() {
        when(inviteMapper.selectOne(any(Wrapper.class))).thenReturn(invite(false));
        CompanyMember active = new CompanyMember();
        active.setStatus("ACTIVE");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(active);
        assertThatThrownBy(() -> service.joinCompany(new JoinRequest("JOIN1234")))
                .hasMessage("你已是该企业成员");

        active.setStatus("PENDING");
        assertThatThrownBy(() -> service.joinCompany(new JoinRequest("JOIN1234")))
                .hasMessage("申请已提交，等待审批");
    }

    @Test
    @SuppressWarnings("unchecked")
    void counterpartyInviteRequiresLegalMemberAndBuildsRelation() {
        CompanyInvite invite = invite(true);
        invite.setRelationRole("supplier");
        when(inviteMapper.selectOne(any(Wrapper.class))).thenReturn(invite);
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.joinCompany(new JoinRequest("PARTNER1")))
                .hasMessageContaining("仅公司法人");

        CompanyMember legal = new CompanyMember();
        legal.setCompanyId(8L);
        legal.setRoleCode("LEGAL");
        legal.setStatus("ACTIVE");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(legal);
        when(companyMapper.selectById(8L)).thenReturn(company(8L, "接受方"));
        when(companyMapper.selectById(3L)).thenReturn(company(3L, "邀请方"));

        JoinResult result = service.joinCompany(new JoinRequest("PARTNER1"));

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.message()).contains("客户关系");
        ArgumentCaptor<CounterpartyRelationEntity> captor = ArgumentCaptor.forClass(CounterpartyRelationEntity.class);
        verify(relationMapper).insert(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(8L);
        assertThat(captor.getValue().getCounterpartyCompanyId()).isEqualTo(3L);
        assertThat(captor.getValue().getCounterpartyCompanyName()).isEqualTo("邀请方");
    }

    @Test
    void approvesMembersAndManagesCustomRolesWithinTenant() {
        CompanyMember pending = new CompanyMember();
        pending.setId(12L);
        pending.setCompanyId(3L);
        pending.setUserId(8L);
        pending.setStatus("PENDING");
        RoleDef finance = new RoleDef();
        finance.setCompanyId(3L);
        finance.setCode("FINANCE");
        finance.setName("财务");
        finance.setPermissions("[\"invoice_view\"]");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(finance);
        AuthorizationRecord approved = service.approveMember("12",
                new ApproveRequest("FINANCE", List.of("order_view")), "3");
        assertThat(approved.status()).isEqualTo("ACTIVE");
        assertThat(approved.roleText()).isEqualTo("财务");
        verify(accessControl).requireManager(3L);
        verify(memberMapper).update(any(Wrapper.class));

        doAnswer(invocation -> {
            RoleDef role = invocation.getArgument(0);
            role.setId(15L);
            return 1;
        }).when(roleMapper).insert(any(RoleDef.class));
        RolePayload role = service.createRole(new RoleRequest("3", "审计", List.of("order_view")));
        assertThat(role.id()).isEqualTo("15");
        assertThat(role.name()).isEqualTo("审计");
        assertThat(role.code()).startsWith("CUSTOM_");
    }

    @Test
    void mapsMemberDisplayNamesAndSupportsScopedRemoval() {
        when(memberMapper.selectAuthorizationRecords(3L)).thenReturn(List.of(
                Map.of("id", 12L, "userId", 7L, "nickname", "新用户", "phone", "13800000000",
                        "roleCode", "ADMIN", "status", "ACTIVE"),
                Map.of("id", 13L, "userId", 8L, "nickname", "张三", "phone", "",
                        "roleCode", "FINANCE", "status", "PENDING")
        ));

        List<AuthorizationRecord> members = service.listMembers("3");
        assertThat(members).extracting(AuthorizationRecord::memberName)
                .containsExactly("用户0000", "张三");
        assertThat(members).extracting(AuthorizationRecord::roleText)
                .containsExactly("管理员", "财务");

        CompanyMember removable = new CompanyMember();
        removable.setId(12L);
        removable.setCompanyId(3L);
        removable.setUserId(8L);
        removable.setRoleCode("FINANCE");
        removable.setStatus("ACTIVE");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(removable);
        service.rejectMember("13", "3");
        service.removeMember("12", "3");
        verify(accessControl, org.mockito.Mockito.times(2)).requireManager(3L);
        verify(memberMapper, org.mockito.Mockito.times(2)).delete(any(Wrapper.class));
    }

    @Test
    void pagesMembersWithinTheResolvedTenant() {
        when(accessControl.resolveCompanyId("3")).thenReturn(3L);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(21L);
        when(memberMapper.selectAuthorizationPageRecords(3L, "ACTIVE", 10, 10L)).thenReturn(List.of(
                Map.of("id", 12L, "userId", 7L, "nickname", "张三", "phone", "13800000000",
                        "roleCode", "ADMIN", "status", "ACTIVE")
        ));

        var result = service.pageMembers("3", " ACTIVE ", 2, 10);

        assertThat(result.total()).isEqualTo(21);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.items()).extracting(AuthorizationRecord::memberName).containsExactly("张三");
    }

    @Test
    void listsUpdatesAndDeletesRolesWithinOwningCompany() {
        when(accessControl.resolveCompanyId("3")).thenReturn(3L);
        RoleDef existing = new RoleDef();
        existing.setId(15L);
        existing.setCompanyId(3L);
        existing.setCode("CUSTOM_AUDIT");
        existing.setName("审计");
        existing.setPermissions("[\"order_view\"]");
        existing.setSystemRole(false);
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        assertThat(service.listRoles("3")).hasSize(1);

        RoleRequest update = new RoleRequest("3", "高级审计", List.of("order_view", "invoice_view"));
        when(roleMapper.selectById(15L)).thenReturn(existing);
        service.updateRole("15", update);
        verify(accessControl, org.mockito.Mockito.times(2)).requireManager(3L);
        verify(roleMapper).update(any(Wrapper.class));

        when(roleMapper.selectById(15L)).thenReturn(null);
        service.deleteRole("15");

        when(roleMapper.selectById(15L)).thenReturn(existing);
        service.deleteRole("15");
        verify(roleMapper).deleteById(15L);
    }

    @Test
    void blocksDirectLegalAssignmentAndSystemRoleMutation() {
        CompanyMember pending = new CompanyMember();
        pending.setId(12L);
        pending.setCompanyId(3L);
        pending.setUserId(8L);
        pending.setStatus("PENDING");
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        assertThatThrownBy(() -> service.approveMember("12", new ApproveRequest("LEGAL", List.of()), "3"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该角色不能通过成员审批分配");

        RoleDef systemRole = new RoleDef();
        systemRole.setId(2L);
        systemRole.setCompanyId(3L);
        systemRole.setCode("ADMIN");
        systemRole.setSystemRole(true);
        when(roleMapper.selectById(2L)).thenReturn(systemRole);
        RoleRequest request = new RoleRequest("3", "管理员", List.of("member_manage"));
        assertThatThrownBy(() -> service.updateRole("2", request)).hasMessage("系统角色不可编辑");
        assertThatThrownBy(() -> service.deleteRole("2")).hasMessage("系统角色不可删除");
    }

    private Company company(long id, String name) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setCreditCode("CODE-" + id);
        company.setLegalPersonName("法人");
        company.setCertificationStatus("VERIFIED");
        company.setRealNameStatus("VERIFIED");
        company.setFaceStatus("VERIFIED");
        company.setSealStatus("UPLOADED");
        return company;
    }

    private CompanyInvite invite(boolean counterparty) {
        CompanyInvite invite = new CompanyInvite();
        invite.setId(10L);
        invite.setCompanyId(3L);
        invite.setCode("JOIN1234");
        invite.setType(counterparty ? "counterparty" : "member");
        invite.setUsed(false);
        invite.setExpiresAt(LocalDateTime.now().plusHours(1));
        return invite;
    }
}
