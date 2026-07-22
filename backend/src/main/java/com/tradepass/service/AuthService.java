package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.CompanyRole;
import com.tradepass.common.TradePassDtos.DevUser;
import com.tradepass.common.TradePassDtos.LoginSession;
import com.tradepass.common.TradePassDtos.MePayload;
import com.tradepass.common.TradePassDtos.MemberInfo;
import com.tradepass.common.TradePassDtos.UserProfile;
import com.tradepass.dto.request.BindCompanyRequest;
import com.tradepass.dto.request.BindPhoneRequest;
import com.tradepass.dto.request.SwitchCompanyRequest;
import com.tradepass.dto.request.SwitchUserRequest;
import com.tradepass.dto.request.WechatLoginRequest;
import com.tradepass.dto.response.TodoItem;
import com.tradepass.entity.Company;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.SysUser;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.PermDefMapper;
import com.tradepass.mapper.SysUserMapper;
import com.tradepass.mapper.TradeContractMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final PermDefMapper permDefMapper;
    private final TradeContractMapper tradeContractMapper;
    private final WechatService wechatService;
    private final RolePermissionService rolePermissionService;
    private final AccessControlService accessControlService;
    private final AuthSessionService authSessionService;
    private final boolean devEnabled;

    public AuthService(SysUserMapper sysUserMapper,
                       CompanyMapper companyMapper,
                       CompanyMemberMapper companyMemberMapper,
                       PermDefMapper permDefMapper,
                       TradeContractMapper tradeContractMapper,
                       WechatService wechatService,
                       RolePermissionService rolePermissionService,
                       AccessControlService accessControlService,
                       AuthSessionService authSessionService,
                       @Value("${tradepass.dev.enabled:false}") boolean devEnabled) {
        this.sysUserMapper = sysUserMapper;
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.permDefMapper = permDefMapper;
        this.tradeContractMapper = tradeContractMapper;
        this.wechatService = wechatService;
        this.rolePermissionService = rolePermissionService;
        this.accessControlService = accessControlService;
        this.authSessionService = authSessionService;
        this.devEnabled = devEnabled;
    }

    @Transactional
    public LoginSession wechatLogin(WechatLoginRequest request) {
        String openid = wechatService.resolveOpenid(request.code());
        if (!devEnabled && request.phone() != null && !request.phone().isBlank()) {
            throw new BusinessException("生产环境不接受未经验证的手机号");
        }
        String phone = request.phoneCode() != null && !request.phoneCode().isBlank()
                ? wechatService.resolvePhoneByCode(request.phoneCode())
                : (devEnabled ? request.phone() : null);

        SysUser user;
        if (openid.startsWith("dev-phone-") && phone != null && !phone.isBlank()) {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .orderByAsc(SysUser::getId)
                    .last("LIMIT 1"));
        } else {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getOpenid, openid)
                    .last("LIMIT 1"));
        }
        if (user == null) {
            user = new SysUser();
            user.setOpenid(openid);
            user.setNickname(request.nickName() != null && !request.nickName().isBlank() ? request.nickName() : "微信用户");
            user.setPhone(phone != null ? phone : "");
            user.setStatus("ACTIVE");
            sysUserMapper.insert(user);
            return new LoginSession(tokenFor(user.getId()), new UserProfile(String.valueOf(user.getId()), openid,
                    user.getPhone(), user.getNickname(), null, "GUEST"));
        }

        if (request.nickName() != null && !request.nickName().isBlank()) {
            user.setNickname(request.nickName());
        }
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone);
        }
        sysUserMapper.updateById(user);

        List<CompanyRole> companies = loadUserCompanies(user.getId());
        String currentCompanyId = companies.isEmpty() ? null : companies.get(0).companyId();
        MemberInfo member = loadMemberAnyCompany(user.getId());
        String roleCode = member == null ? "GUEST" : member.roleCode();
        return new LoginSession(tokenFor(user.getId()), new UserProfile(String.valueOf(user.getId()), openid,
                user.getPhone(), user.getNickname(), currentCompanyId, roleCode));
    }

    public UserProfile bindPhone(BindPhoneRequest request) {
        if (!devEnabled) {
            throw new BusinessException("请使用微信手机号验证完成绑定");
        }
        long userId = AuthContext.userId();
        Long companyId = AuthContext.companyId();
        sysUserMapper.update(new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId).set(SysUser::getPhone, request.phone()));
        MemberInfo member = loadMember(userId, companyId == null ? 0L : companyId);
        if (member == null) {
            return null;
        }
        return new UserProfile(member.userId(), "demo-openid", request.phone(), member.userName(),
                companyId == null ? null : String.valueOf(companyId), member.roleCode());
    }

    public MePayload me() {
        return buildMe(AuthContext.userId(), AuthContext.companyId());
    }

    public List<Map<String, Object>> permissions() {
        return permDefMapper.selectMaps(new LambdaQueryWrapper<com.tradepass.entity.PermDef>()
                .select(com.tradepass.entity.PermDef::getCode, com.tradepass.entity.PermDef::getLabel)
                .orderByAsc(com.tradepass.entity.PermDef::getSortOrder));
    }

    public List<TodoItem> myTodos() {
        long userId = AuthContext.userId();
        Long companyId = AuthContext.companyId();
        List<TodoItem> todos = new ArrayList<>();
        if (companyId == null) {
            return todos;
        }

        boolean manager = accessControlService.hasPermission(companyId, "member_manage")
                || accessControlService.hasPermission(companyId, "auth_manage");
        if (!manager) {
            return todos;
        }

        Long pending = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getStatus, "PENDING"));
        if (pending > 0) {
            todos.add(new TodoItem("APPROVAL", "成员待审批", pending + " 位同事申请加入企业", pending.intValue(), "auth-manage"));
        }

        Company company = companyMapper.selectById(companyId);
        if (company != null && company.getCertificationStatus() != null && !"VERIFIED".equals(company.getCertificationStatus())) {
            todos.add(new TodoItem("CERT", "企业认证待完成", "完成认证后可使用全部签约能力", 1, "company-cert"));
        }
        if (company != null) {
            Long pendingContracts = tradeContractMapper.selectCount(new LambdaQueryWrapper<TradeContract>()
                    .eq(TradeContract::getCounterpartyCompanyId, companyId)
                    .eq(TradeContract::getStatus, "PENDING"));
            if (pendingContracts > 0) {
                todos.add(new TodoItem("CONTRACT", "合同待审批", pendingContracts + " 份合同等待你方签署", pendingContracts.intValue(), "contract-approval"));
            }
        }
        return todos;
    }

    public MePayload switchCompany(SwitchCompanyRequest request) {
        long userId = AuthContext.userId();
        long newCompanyId = parseId(request.companyId());
        boolean exists = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, newCompanyId)
                .eq(CompanyMember::getUserId, userId)
                .eq(CompanyMember::getStatus, "ACTIVE")) > 0;
        if (!exists) {
            throw new BusinessException("你不是该公司成员");
        }
        return buildMe(userId, newCompanyId);
    }

    @Transactional
    public MePayload bindCompany(BindCompanyRequest request) {
        long userId = AuthContext.userId();
        long companyId = parseId(request.id());
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在，请先提交企业资料");
        }
        if (!company.getName().equals(request.name())
                || !company.getCreditCode().equals(request.creditCode())
                || !company.getLegalPersonName().equals(request.legalPersonName())) {
            throw new BusinessException("企业资料与已提交记录不一致");
        }

        CompanyMember existing = companyMemberMapper.selectOne(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            return buildMe(userId, companyId);
        }

        if (company.getCreatedBy() == null || company.getCreatedBy() != userId) {
            throw new BusinessException("该企业已存在，请通过企业邀请码加入或提交人工认领申请");
        }

        CompanyMember member = new CompanyMember();
        member.setCompanyId(companyId);
        member.setUserId(userId);
        member.setRoleCode("LEGAL_CANDIDATE");
        member.setIsLegalPerson(false);
        member.setIsAdministrator(false);
        member.setStatus("PENDING");
        companyMemberMapper.insert(member);
        return buildMe(userId, companyId);
    }

    public void logout(String authorization) {
        authSessionService.revoke(authorization);
    }

    public List<DevUser> listDevUsers() {
        return companyMemberMapper.selectDevUsers().stream()
                .map(row -> {
                    String roleCode = string(row.get("roleCode"));
                    return new DevUser(String.valueOf(row.get("id")), string(row.get("nickname")), string(row.get("phone")),
                            roleCode, rolePermissionService.roleText(roleCode));
                })
                .toList();
    }

    public LoginSession switchUser(SwitchUserRequest request) {
        long userId = parseId(request.userId());
        MemberInfo member = loadMemberAnyCompany(userId);
        if (member == null) {
            return null;
        }
        List<CompanyRole> companies = loadUserCompanies(userId);
        String currentCompanyId = companies.isEmpty() ? null : companies.get(0).companyId();
        UserProfile user = new UserProfile(member.userId(), "dev-openid", member.phone(), member.userName(), currentCompanyId, member.roleCode());
        return new LoginSession(tokenFor(userId), user);
    }

    public MemberInfo loadMember(long userId, long companyId) {
        return toMemberInfo(companyMemberMapper.selectMemberInfo(userId, companyId), companyId);
    }

    public MemberInfo loadMemberAnyCompany(long userId) {
        Map<String, Object> row = companyMemberMapper.selectMemberInfoAnyCompany(userId);
        Long companyId = row == null || row.get("companyId") == null ? null : Long.parseLong(String.valueOf(row.get("companyId")));
        return toMemberInfo(row, companyId);
    }

    public List<CompanyRole> loadUserCompanies(long userId) {
        return companyMemberMapper.selectUserCompanies(userId).stream()
                .map(row -> {
                    String roleCode = string(row.get("roleCode"));
                    return new CompanyRole(String.valueOf(row.get("companyId")), string(row.get("companyName")),
                            roleCode, row.get("roleName") == null
                                    ? rolePermissionService.roleText(roleCode)
                                    : string(row.get("roleName")));
                })
                .toList();
    }

    private MePayload buildMe(long userId, Long companyId) {
        List<CompanyRole> companies = loadUserCompanies(userId);
        Long effectiveCompanyId = companyId;
        if (effectiveCompanyId == null && !companies.isEmpty()) {
            effectiveCompanyId = parseId(companies.get(0).companyId());
        }
        MemberInfo member = loadMember(userId, effectiveCompanyId == null ? 0L : effectiveCompanyId);
        if (member == null || member.roleCode() == null) {
            SysUser user = sysUserMapper.selectById(userId);
            String nick = user == null ? "微信用户" : user.getNickname();
            String phone = user == null ? "" : user.getPhone();
            UserProfile profile = new UserProfile(String.valueOf(userId), "demo-openid", phone, nick, null, "GUEST");
            return new MePayload(profile, fallbackCompany(), null, companies);
        }
        CompanyProfile company = effectiveCompanyId == null ? null : toCompanyProfile(companyMapper.selectById(effectiveCompanyId));
        UserProfile profile = new UserProfile(member.userId(), "demo-openid", member.phone(), member.userName(),
                effectiveCompanyId == null ? null : String.valueOf(effectiveCompanyId), member.roleCode());
        return new MePayload(profile, company != null ? company : fallbackCompany(), member, companies);
    }

    private MemberInfo toMemberInfo(Map<String, Object> row, Long companyId) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String roleCode = string(row.get("roleCode"));
        String status = string(row.get("status"));
        AccessControlService.EffectiveRole role = companyId == null || !"ACTIVE".equals(status)
                ? new AccessControlService.EffectiveRole("GUEST", "访客", List.of())
                : accessControlService.effectiveRole(companyId, Long.parseLong(String.valueOf(row.get("userId"))));
        return new MemberInfo(
                String.valueOf(row.get("userId")),
                string(row.get("nickname")),
                string(row.get("phone")),
                roleCode != null ? roleCode : "GUEST",
                role.name(),
                role.permissions(),
                status != null ? status : "NONE"
        );
    }

    public CompanyProfile toCompanyProfile(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanyProfile(String.valueOf(company.getId()), company.getName(), company.getCreditCode(),
                company.getLegalPersonName(), company.getRegisteredAddress(), company.getContactPhone(),
                company.getBankName(), company.getBankAccount(), company.getCertificationStatus(), company.getRealNameStatus(),
                company.getFaceStatus(), company.getSealStatus());
    }

    private CompanyProfile fallbackCompany() {
        return new CompanyProfile("", "未加入企业", "", "", null, null, null, null,
                "NOT_SUBMITTED", "NOT_STARTED", "NOT_STARTED", "NOT_UPLOADED");
    }

    private String tokenFor(long userId) {
        return authSessionService.issue(userId);
    }

    private long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException("ID 格式不正确");
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
