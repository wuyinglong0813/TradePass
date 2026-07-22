package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.AuthorizationRecord;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.CompanySearchSummary;
import com.tradepass.common.TradePassDtos.SealRecord;
import com.tradepass.dto.request.ApproveRequest;
import com.tradepass.dto.request.CompanySubmitRequest;
import com.tradepass.dto.request.InviteRequest;
import com.tradepass.dto.request.JoinRequest;
import com.tradepass.dto.request.RoleRequest;
import com.tradepass.dto.request.SealRequest;
import com.tradepass.dto.request.VerificationRequest;
import com.tradepass.dto.response.InviteResult;
import com.tradepass.dto.response.JoinResult;
import com.tradepass.dto.response.PagePayload;
import com.tradepass.dto.response.RolePayload;
import com.tradepass.entity.Company;
import com.tradepass.entity.CompanyInvite;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.CounterpartyRelationEntity;
import com.tradepass.entity.RoleDef;
import com.tradepass.mapper.CompanyInviteMapper;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.mapper.PermDefMapper;
import com.tradepass.mapper.RoleDefMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class CompanyService {
    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final CompanyInviteMapper companyInviteMapper;
    private final CounterpartyRelationMapper counterpartyRelationMapper;
    private final RoleDefMapper roleDefMapper;
    private final PermDefMapper permDefMapper;
    private final AccessControlService accessControlService;
    private final CompanySearchRateLimiter companySearchRateLimiter;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLogService;
    private final boolean verificationAutoApprove;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompanyService(CompanyMapper companyMapper,
                          CompanyMemberMapper companyMemberMapper,
                          CompanyInviteMapper companyInviteMapper,
                          CounterpartyRelationMapper counterpartyRelationMapper,
                          RoleDefMapper roleDefMapper,
                          PermDefMapper permDefMapper,
                          AccessControlService accessControlService,
                          CompanySearchRateLimiter companySearchRateLimiter,
                          RolePermissionService rolePermissionService,
                          AuditLogService auditLogService,
                          @Value("${tradepass.verification.auto-approve:false}") boolean verificationAutoApprove) {
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.companyInviteMapper = companyInviteMapper;
        this.counterpartyRelationMapper = counterpartyRelationMapper;
        this.roleDefMapper = roleDefMapper;
        this.permDefMapper = permDefMapper;
        this.accessControlService = accessControlService;
        this.companySearchRateLimiter = companySearchRateLimiter;
        this.rolePermissionService = rolePermissionService;
        this.auditLogService = auditLogService;
        this.verificationAutoApprove = verificationAutoApprove;
    }

    public List<CompanySearchSummary> searchCompanies(String keyword) {
        companySearchRateLimiter.check(AuthContext.userId());
        String kw = keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }
        if (kw.length() < 2) {
            throw new BusinessException("搜索关键词至少需要 2 个字符");
        }
        if (kw.length() > 50 || kw.contains("%") || kw.contains("_")) {
            throw new BusinessException("搜索关键词格式不正确");
        }
        return companyMapper.selectList(new LambdaQueryWrapper<Company>()
                        .and(query -> query.like(Company::getName, kw).or().like(Company::getCreditCode, kw))
                        .orderByAsc(Company::getName)
                        .last("LIMIT 20"))
                .stream().map(this::toCompanySearchSummary).toList();
    }

    public CompanyProfile getCompany(String id) {
        long companyId = parseId(id);
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        AccessControlService.CompanyProfileAccess access = accessControlService.requireCompanyProfileAccess(companyId);
        return access == AccessControlService.CompanyProfileAccess.SENSITIVE_OWNER
                ? toCompanyProfile(company)
                : toRestrictedCompanyProfile(company);
    }

    @Transactional
    public CompanyProfile submitCompany(CompanySubmitRequest request) {
        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>().eq(Company::getCreditCode, request.creditCode()).last("LIMIT 1"));
        if (company == null) {
            company = new Company();
            company.setCreditCode(request.creditCode());
            company.setCreatedBy(AuthContext.userId());
            company.setCertificationStatus("PENDING");
            company.setRealNameStatus("NOT_STARTED");
            company.setFaceStatus("NOT_STARTED");
            company.setSealStatus("NOT_UPLOADED");
        } else if (company.getCreatedBy() == null || company.getCreatedBy() != AuthContext.userId()) {
            throw new BusinessException("企业已入驻，请通过企业邀请或认领流程加入");
        }
        company.setName(request.name());
        company.setLegalPersonName(request.legalPersonName());
        company.setRegisteredAddress(trim(request.registeredAddress()));
        company.setContactPhone(trim(request.contactPhone()));
        company.setBankName(trim(request.bankName()));
        company.setBankAccount(trim(request.bankAccount()));
        if (companyMapper.selectById(company.getId()) == null) {
            companyMapper.insert(company);
        } else {
            companyMapper.updateById(company);
        }
        return toCompanyProfile(companyMapper.selectById(company.getId()));
    }

    public CompanyProfile submitCertification(String id) {
        accessControlService.requireLegalOrClaim(parseId(id));
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(id))
                .set(Company::getCertificationStatus, "PENDING_REVIEW"));
        return getCompany(id);
    }

    public CompanyProfile verifyRealName(VerificationRequest req) {
        requireVerificationProvider();
        accessControlService.requireLegalOrClaim(parseId(req.companyId()));
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(req.companyId()))
                .set(Company::getRealNameStatus, "VERIFIED"));
        return getCompany(req.companyId());
    }

    public CompanyProfile verifyFace(VerificationRequest req) {
        requireVerificationProvider();
        accessControlService.requireLegalOrClaim(parseId(req.companyId()));
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(req.companyId()))
                .set(Company::getFaceStatus, "VERIFIED"));
        return getCompany(req.companyId());
    }

    public SealRecord uploadSeal(SealRequest req) {
        requireVerificationProvider();
        accessControlService.requireLegalOrClaim(parseId(req.companyId()));
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(req.companyId()))
                .set(Company::getSealStatus, "PENDING_REVIEW"));
        return new SealRecord(UUID.randomUUID().toString(), req.companyId(), req.fileUrl(), req.usage(), "PENDING_REVIEW");
    }

    public InviteResult createInvite(InviteRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireManager(companyId);
        String code = generateInviteCode();
        saveInvite(companyId, code, "member", null);
        return new InviteResult(code, req.companyId());
    }

    public InviteResult createCounterpartyInvite(InviteRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireLegal(companyId);
        String code = generateInviteCode();
        String relationRole = "supplier".equalsIgnoreCase(req.relationRole()) ? "supplier" : "buyer";
        saveInvite(companyId, code, "counterparty", relationRole);
        return new InviteResult(code, req.companyId());
    }

    @Transactional
    public JoinResult joinCompany(JoinRequest req) {
        CompanyInvite invite = companyInviteMapper.selectOne(new LambdaQueryWrapper<CompanyInvite>()
                .eq(CompanyInvite::getCode, req.code())
                .last("LIMIT 1"));
        if (invite == null) {
            throw new BusinessException("邀请码无效");
        }
        if (Boolean.TRUE.equals(invite.getUsed())) {
            throw new BusinessException("邀请码已被使用");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("邀请码已过期");
        }

        long userId = AuthContext.userId();
        if ("counterparty".equals(invite.getType())) {
            CompanyMember legalMember = companyMemberMapper.selectOne(new LambdaQueryWrapper<CompanyMember>()
                    .eq(CompanyMember::getUserId, userId)
                    .eq(CompanyMember::getRoleCode, "LEGAL")
                    .eq(CompanyMember::getStatus, "ACTIVE")
                    .last("LIMIT 1"));
            if (legalMember == null) {
                throw new BusinessException("仅公司法人可接受企业合作邀请，请先在您的公司完成法人认证");
            }
            Company userCompany = companyMapper.selectById(legalMember.getCompanyId());
            Company inviterCompany = companyMapper.selectById(invite.getCompanyId());
            String companyName = userCompany == null ? "未知企业" : userCompany.getName();
            CounterpartyRelationEntity relation = new CounterpartyRelationEntity();
            if ("supplier".equalsIgnoreCase(invite.getRelationRole())) {
                relation.setCompanyId(legalMember.getCompanyId());
                relation.setCounterpartyCompanyId(invite.getCompanyId());
                relation.setCounterpartyCompanyName(inviterCompany == null ? "未知企业" : inviterCompany.getName());
            } else {
                relation.setCompanyId(invite.getCompanyId());
                relation.setCounterpartyCompanyId(legalMember.getCompanyId());
                relation.setCounterpartyCompanyName(companyName);
            }
            relation.setRelationType("SUPPLIER");
            relation.setStatus("ACTIVE");
            try {
                counterpartyRelationMapper.insert(relation);
            } catch (Exception ignored) {
            }
            markInviteUsed(invite, userId);
            String relationText = "supplier".equalsIgnoreCase(invite.getRelationRole()) ? "客户关系" : "供应商关系";
            return new JoinResult("ACTIVE", "已建立" + relationText);
        }

        CompanyMember existing = companyMemberMapper.selectOne(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, invite.getCompanyId())
                .eq(CompanyMember::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            if ("ACTIVE".equals(existing.getStatus())) {
                throw new BusinessException("你已是该企业成员");
            }
            if ("PENDING".equals(existing.getStatus())) {
                throw new BusinessException("申请已提交，等待审批");
            }
        }

        CompanyMember member = new CompanyMember();
        member.setCompanyId(invite.getCompanyId());
        member.setUserId(userId);
        member.setRoleCode("GUEST");
        member.setIsLegalPerson(false);
        member.setIsAdministrator(false);
        member.setStatus("PENDING");
        companyMemberMapper.insert(member);
        markInviteUsed(invite, userId);
        return new JoinResult("PENDING", "申请已提交，等待管理员审批");
    }

    public List<AuthorizationRecord> listMembers(String companyId) {
        return companyMemberMapper.selectAuthorizationRecords(parseId(companyId)).stream()
                .map(row -> toAuthorizationRecord(row, companyId))
                .toList();
    }

    public PagePayload<AuthorizationRecord> pageMembers(String companyId, String status, int page, int size) {
        long cid = accessControlService.resolveCompanyId(companyId);
        accessControlService.requireManager(cid);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String cleanStatus = status == null ? null : status.trim();
        LambdaQueryWrapper<CompanyMember> countQuery = new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, cid);
        if (cleanStatus != null && !cleanStatus.isBlank()) {
            countQuery.eq(CompanyMember::getStatus, cleanStatus);
        }
        long total = companyMemberMapper.selectCount(countQuery);
        long offset = (long) (normalizedPage - 1) * normalizedSize;
        List<AuthorizationRecord> items = companyMemberMapper.selectAuthorizationPageRecords(
                        cid, cleanStatus, normalizedSize, offset).stream()
                .map(row -> toAuthorizationRecord(row, String.valueOf(cid)))
                .toList();
        return PagePayload.of(items, total, normalizedPage, normalizedSize);
    }

    public AuthorizationRecord approveMember(String id, ApproveRequest req, String companyId) {
        long cid = parseId(companyId);
        accessControlService.requireManager(cid);
        long memberId = parseId(id);
        CompanyMember target = companyMemberMapper.selectOne(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getId, memberId)
                .eq(CompanyMember::getCompanyId, cid)
                .eq(CompanyMember::getStatus, "PENDING")
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException("成员申请不存在或状态已变化");
        }
        RoleDef role = requireAssignableRole(cid, req.roleCode());
        List<String> rolePermissions = parsePermissions(role.getPermissions());
        List<String> customPermissions = req.customPermissions() == null ? List.of() : req.customPermissions();
        validateGrantablePermissions(cid, rolePermissions);
        if (!customPermissions.isEmpty()) {
            validateGrantablePermissions(cid, customPermissions);
        }
        String perms = !customPermissions.isEmpty()
                ? toJson(customPermissions)
                : null;
        int updated = companyMemberMapper.update(new LambdaUpdateWrapper<CompanyMember>()
                .eq(CompanyMember::getId, memberId)
                .eq(CompanyMember::getCompanyId, cid)
                .eq(CompanyMember::getStatus, "PENDING")
                .set(CompanyMember::getRoleCode, role.getCode())
                .set(CompanyMember::getCustomPermissions, perms)
                .set(CompanyMember::getIsLegalPerson, false)
                .set(CompanyMember::getIsAdministrator, "ADMIN".equals(role.getCode()))
                .set(CompanyMember::getStatus, "ACTIVE"));
        if (updated != 1) {
            throw new BusinessException("成员申请状态已变化，请刷新后重试");
        }
        Set<String> effective = new LinkedHashSet<>(rolePermissions);
        effective.addAll(customPermissions);
        auditLogService.log(cid, "COMPANY_MEMBER", memberId, "APPROVE", "分配角色 " + role.getCode());
        return new AuthorizationRecord(id, companyId, String.valueOf(target.getUserId()), "", role.getCode(),
                role.getName(), List.copyOf(effective), "ACTIVE", "");
    }

    public void rejectMember(String id, String companyId) {
        long cid = parseId(companyId);
        accessControlService.requireManager(cid);
        int deleted = companyMemberMapper.delete(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getId, parseId(id))
                .eq(CompanyMember::getCompanyId, cid)
                .eq(CompanyMember::getStatus, "PENDING"));
        if (deleted != 1) {
            throw new BusinessException("成员申请不存在或状态已变化");
        }
        auditLogService.log(cid, "COMPANY_MEMBER", id, "REJECT", "拒绝成员加入申请");
    }

    public void removeMember(String id, String companyId) {
        long cid = parseId(companyId);
        accessControlService.requireManager(cid);
        long memberId = parseId(id);
        CompanyMember target = companyMemberMapper.selectOne(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getId, memberId)
                .eq(CompanyMember::getCompanyId, cid)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException("成员不存在");
        }
        if (target.getUserId() == AuthContext.userId()) {
            throw new BusinessException("不能移除当前登录成员");
        }
        if (Boolean.TRUE.equals(target.getIsLegalPerson()) || "LEGAL".equals(target.getRoleCode())) {
            throw new BusinessException("法人只能通过法人变更流程移交");
        }
        if ("ADMIN".equals(target.getRoleCode()) && companyMemberMapper.selectCount(
                new LambdaQueryWrapper<CompanyMember>()
                        .eq(CompanyMember::getCompanyId, cid)
                        .eq(CompanyMember::getRoleCode, "ADMIN")
                        .eq(CompanyMember::getStatus, "ACTIVE")) <= 1) {
            throw new BusinessException("企业至少需要保留一名管理员");
        }
        int deleted = companyMemberMapper.delete(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getId, memberId)
                .eq(CompanyMember::getCompanyId, cid)
                .eq(CompanyMember::getStatus, "ACTIVE"));
        if (deleted != 1) {
            throw new BusinessException("成员状态已变化，请刷新后重试");
        }
        auditLogService.log(cid, "COMPANY_MEMBER", memberId, "REMOVE", "移除企业成员");
    }

    public List<RolePayload> listRoles(String companyId) {
        long cid = accessControlService.resolveCompanyId(companyId);
        accessControlService.requireManager(cid);
        return roleDefMapper.selectList(new LambdaQueryWrapper<RoleDef>()
                        .eq(RoleDef::getCompanyId, cid)
                        .orderByDesc(RoleDef::getSystemRole)
                        .orderByAsc(RoleDef::getId))
                .stream().map(this::toRolePayload).toList();
    }

    public RolePayload createRole(RoleRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireManager(companyId);
        validateGrantablePermissions(companyId, req.permissions());
        RoleDef role = new RoleDef();
        role.setCompanyId(companyId);
        role.setCode("CUSTOM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        role.setName(req.name());
        role.setSystemRole(false);
        role.setPermissions(toJson(req.permissions()));
        roleDefMapper.insert(role);
        auditLogService.log(companyId, "ROLE", role.getId(), "CREATE", "创建角色 " + role.getCode());
        return toRolePayload(role);
    }

    public void updateRole(String id, RoleRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireManager(companyId);
        RoleDef existing = roleDefMapper.selectById(parseId(id));
        if (existing == null || existing.getCompanyId() != companyId) {
            throw new BusinessException("角色不存在");
        }
        if (Boolean.TRUE.equals(existing.getSystemRole())) {
            throw new BusinessException("系统角色不可编辑");
        }
        validateGrantablePermissions(companyId, req.permissions());
        int updated = roleDefMapper.update(new LambdaUpdateWrapper<RoleDef>()
                .eq(RoleDef::getId, existing.getId())
                .eq(RoleDef::getCompanyId, companyId)
                .set(RoleDef::getName, req.name())
                .set(RoleDef::getPermissions, toJson(req.permissions())));
        if (updated != 1) {
            throw new BusinessException("角色状态已变化，请刷新后重试");
        }
        auditLogService.log(companyId, "ROLE", existing.getId(), "UPDATE", "更新角色 " + existing.getCode());
    }

    public void deleteRole(String id) {
        RoleDef role = roleDefMapper.selectById(parseId(id));
        if (role == null) {
            return;
        }
        accessControlService.requireManager(role.getCompanyId());
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            throw new BusinessException("系统角色不可删除");
        }
        if (companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, role.getCompanyId())
                .eq(CompanyMember::getRoleCode, role.getCode())) > 0) {
            throw new BusinessException("角色仍有成员使用，请先迁移成员角色");
        }
        roleDefMapper.deleteById(role.getId());
        auditLogService.log(role.getCompanyId(), "ROLE", role.getId(), "DELETE", "删除角色 " + role.getCode());
    }

    private void saveInvite(long companyId, String code, String type, String relationRole) {
        CompanyInvite invite = new CompanyInvite();
        invite.setCompanyId(companyId);
        invite.setCode(code);
        invite.setType(type);
        invite.setRelationRole(relationRole);
        invite.setUsed(false);
        invite.setExpiresAt(LocalDateTime.now().plusHours(24));
        companyInviteMapper.insert(invite);
    }

    private void markInviteUsed(CompanyInvite invite, long userId) {
        invite.setUsed(true);
        invite.setUsedBy(userId);
        companyInviteMapper.updateById(invite);
    }

    private CompanyProfile toCompanyProfile(Company company) {
        return new CompanyProfile(String.valueOf(company.getId()), company.getName(), company.getCreditCode(),
                company.getLegalPersonName(), company.getRegisteredAddress(), company.getContactPhone(),
                company.getBankName(), company.getBankAccount(), company.getCertificationStatus(), company.getRealNameStatus(),
                company.getFaceStatus(), company.getSealStatus());
    }

    private CompanySearchSummary toCompanySearchSummary(Company company) {
        return new CompanySearchSummary(String.valueOf(company.getId()), company.getName(),
                maskCreditCode(company.getCreditCode()), "VERIFIED".equals(company.getCertificationStatus()));
    }

    private CompanyProfile toRestrictedCompanyProfile(Company company) {
        return new CompanyProfile(String.valueOf(company.getId()), company.getName(), company.getCreditCode(),
                company.getLegalPersonName(), company.getRegisteredAddress(), maskPhone(company.getContactPhone()),
                company.getBankName(), maskBankAccount(company.getBankAccount()), company.getCertificationStatus(),
                null, null, company.getSealStatus());
    }

    private String maskCreditCode(String value) {
        return maskMiddle(value, 4, 4);
    }

    private String maskPhone(String value) {
        return maskMiddle(value, 3, 4);
    }

    private String maskBankAccount(String value) {
        return maskMiddle(value, 0, 4);
    }

    private String maskMiddle(String value, int visiblePrefix, int visibleSuffix) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int length = value.length();
        if (length <= visiblePrefix + visibleSuffix) {
            return "*".repeat(length);
        }
        return value.substring(0, visiblePrefix)
                + "*".repeat(length - visiblePrefix - visibleSuffix)
                + value.substring(length - visibleSuffix);
    }

    private AuthorizationRecord toAuthorizationRecord(Map<String, Object> row, String companyId) {
        String roleCode = string(row.get("roleCode"));
        String name = string(row.get("nickname"));
        String phone = string(row.get("phone"));
        String displayName = name != null && !name.isBlank() && !"新用户".equals(name)
                ? name
                : (phone != null && !phone.isBlank() ? "用户" + phone.substring(phone.length() - 4) : "微信用户");
        String status = string(row.get("status"));
        AccessControlService.EffectiveRole effective = "ACTIVE".equals(status)
                ? accessControlService.effectiveRole(parseId(companyId), parseId(string(row.get("userId"))))
                : new AccessControlService.EffectiveRole(roleCode, rolePermissionService.roleText(roleCode), List.of());
        return new AuthorizationRecord(String.valueOf(row.get("id")), companyId, String.valueOf(row.get("userId")),
                displayName, effective.code(), effective.name(), effective.permissions(), status,
                phone != null ? phone : "");
    }

    private RoleDef requireAssignableRole(long companyId, String roleCode) {
        if (roleCode == null || roleCode.isBlank() || "LEGAL".equals(roleCode)) {
            throw new BusinessException("该角色不能通过成员审批分配");
        }
        RoleDef role = roleDefMapper.selectOne(new LambdaQueryWrapper<RoleDef>()
                .eq(RoleDef::getCompanyId, companyId)
                .eq(RoleDef::getCode, roleCode)
                .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException("角色不存在或不属于当前企业");
        }
        return role;
    }

    private void validateGrantablePermissions(long companyId, List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new BusinessException("角色至少需要一个权限");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String permission : permissions) {
            String code = permission == null ? "" : permission.trim();
            if (code.isBlank() || "all".equals(code)) {
                throw new BusinessException("不能配置保留权限 " + code);
            }
            if (!unique.add(code)) {
                throw new BusinessException("权限配置存在重复项 " + code);
            }
            if (permDefMapper.selectById(code) == null) {
                throw new BusinessException("未知权限 " + code);
            }
            if (!accessControlService.hasPermission(companyId, code)) {
                throw new BusinessException("不能授予当前操作者不具备的权限 " + code);
            }
        }
    }

    private List<String> parsePermissions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new BusinessException("权限配置格式错误");
        }
    }

    private RolePayload toRolePayload(RoleDef role) {
        boolean systemRole = Boolean.TRUE.equals(role.getSystemRole());
        return new RolePayload(String.valueOf(role.getId()), role.getCode(), role.getName(),
                parsePermissions(role.getPermissions()), systemRole, !systemRole, !systemRole);
    }

    private void requireVerificationProvider() {
        if (!verificationAutoApprove) {
            throw new BusinessException("认证服务尚未配置，无法完成该操作");
        }
    }

    private String toJson(List<String> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception e) {
            throw new BusinessException("权限配置格式错误");
        }
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var sb = new StringBuilder();
        var rng = new java.security.SecureRandom();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
