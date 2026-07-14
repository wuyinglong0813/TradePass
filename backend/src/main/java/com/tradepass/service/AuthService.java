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

    public AuthService(SysUserMapper sysUserMapper,
                       CompanyMapper companyMapper,
                       CompanyMemberMapper companyMemberMapper,
                       PermDefMapper permDefMapper,
                       TradeContractMapper tradeContractMapper,
                       WechatService wechatService,
                       RolePermissionService rolePermissionService) {
        this.sysUserMapper = sysUserMapper;
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.permDefMapper = permDefMapper;
        this.tradeContractMapper = tradeContractMapper;
        this.wechatService = wechatService;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional
    public LoginSession wechatLogin(WechatLoginRequest request) {
        String openid = wechatService.resolveOpenid(request.code());
        String phone = request.phoneCode() != null && !request.phoneCode().isBlank()
                ? wechatService.resolvePhoneByCode(request.phoneCode())
                : request.phone();

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

        boolean manager = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, userId)
                .eq(CompanyMember::getStatus, "ACTIVE")
                .in(CompanyMember::getRoleCode, List.of("LEGAL", "ADMIN"))) > 0;
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
                    .eq(TradeContract::getCounterpartyName, company.getName())
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
            company = new Company();
            company.setId(companyId);
            company.setName(request.name());
            company.setCreditCode(request.creditCode());
            company.setLegalPersonName(request.legalPersonName());
            company.setCertificationStatus("PENDING");
            company.setRealNameStatus("NOT_STARTED");
            company.setFaceStatus("NOT_STARTED");
            company.setSealStatus("NOT_UPLOADED");
            companyMapper.insert(company);
        } else {
            company.setName(request.name());
            company.setCreditCode(request.creditCode());
            company.setLegalPersonName(request.legalPersonName());
            companyMapper.updateById(company);
        }
        if (companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, userId)) == 0) {
            CompanyMember member = new CompanyMember();
            member.setCompanyId(companyId);
            member.setUserId(userId);
            member.setRoleCode("LEGAL");
            member.setIsLegalPerson(true);
            member.setIsAdministrator(false);
            member.setStatus("ACTIVE");
            companyMemberMapper.insert(member);
        }
        return buildMe(userId, companyId);
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
        return toMemberInfo(companyMemberMapper.selectMemberInfo(userId, companyId));
    }

    public MemberInfo loadMemberAnyCompany(long userId) {
        return toMemberInfo(companyMemberMapper.selectMemberInfoAnyCompany(userId));
    }

    public List<CompanyRole> loadUserCompanies(long userId) {
        return companyMemberMapper.selectUserCompanies(userId).stream()
                .map(row -> {
                    String roleCode = string(row.get("roleCode"));
                    return new CompanyRole(String.valueOf(row.get("companyId")), string(row.get("companyName")),
                            roleCode, rolePermissionService.roleText(roleCode));
                })
                .toList();
    }

    private MePayload buildMe(long userId, Long companyId) {
        List<CompanyRole> companies = loadUserCompanies(userId);
        Long effectiveCompanyId = companyId == null && !companies.isEmpty() ? parseId(companies.get(0).companyId()) : companyId;
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

    private MemberInfo toMemberInfo(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        String roleCode = string(row.get("roleCode"));
        String status = string(row.get("status"));
        RolePermissionService.RoleDef role = rolePermissionService.role(roleCode);
        return new MemberInfo(
                String.valueOf(row.get("userId")),
                string(row.get("nickname")),
                string(row.get("phone")),
                roleCode != null ? roleCode : "GUEST",
                role.text(),
                role.permissions(),
                status != null ? status : "NONE"
        );
    }

    public CompanyProfile toCompanyProfile(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanyProfile(String.valueOf(company.getId()), company.getName(), company.getCreditCode(),
                company.getLegalPersonName(), company.getCertificationStatus(), company.getRealNameStatus(),
                company.getFaceStatus(), company.getSealStatus());
    }

    private CompanyProfile fallbackCompany() {
        return new CompanyProfile("1", "未加入企业", "", "", "NOT_SUBMITTED", "NOT_STARTED", "NOT_STARTED", "NOT_UPLOADED");
    }

    private String tokenFor(long userId) {
        return "mvp-token-" + userId;
    }

    private long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
