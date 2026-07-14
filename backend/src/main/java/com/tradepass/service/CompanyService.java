package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.AuthorizationRecord;
import com.tradepass.common.TradePassDtos.CompanyProfile;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CompanyService {
    private final CompanyMapper companyMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final CompanyInviteMapper companyInviteMapper;
    private final CounterpartyRelationMapper counterpartyRelationMapper;
    private final RoleDefMapper roleDefMapper;
    private final AccessControlService accessControlService;
    private final RolePermissionService rolePermissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompanyService(CompanyMapper companyMapper,
                          CompanyMemberMapper companyMemberMapper,
                          CompanyInviteMapper companyInviteMapper,
                          CounterpartyRelationMapper counterpartyRelationMapper,
                          RoleDefMapper roleDefMapper,
                          AccessControlService accessControlService,
                          RolePermissionService rolePermissionService) {
        this.companyMapper = companyMapper;
        this.companyMemberMapper = companyMemberMapper;
        this.companyInviteMapper = companyInviteMapper;
        this.counterpartyRelationMapper = counterpartyRelationMapper;
        this.roleDefMapper = roleDefMapper;
        this.accessControlService = accessControlService;
        this.rolePermissionService = rolePermissionService;
    }

    public List<CompanyProfile> searchCompanies(String keyword) {
        List<CompanyProfile> results = new ArrayList<>();
        String kw = keyword.trim();
        if (kw.isEmpty()) {
            return results;
        }
        record MockCompany(String name, String creditCode, String legalPerson) {}
        List<MockCompany> mockDb = List.of(
                new MockCompany("河北光屿行贸易有限公司", "91130100MA00000001", "满帅"),
                new MockCompany("河北通瑞贸易有限公司", "91130100MA00000002", "李强"),
                new MockCompany("河北逸泽昌贸易有限公司", "91130100MA00000003", "赵伟"),
                new MockCompany("上海远航进出口有限公司", "91310000MA00000002", "王海"),
                new MockCompany("北京华贸联合科技有限公司", "91110108MA00000004", "张明"),
                new MockCompany("深圳前海创新投资有限公司", "91440300MA00000005", "陈磊"),
                new MockCompany("广州亿通物流有限公司", "91440100MA00000006", "刘洋"),
                new MockCompany("杭州智云数据科技有限公司", "91330100MA00000007", "周涛"),
                new MockCompany("成都锦程商贸有限公司", "91510100MA00000008", "孙丽"),
                new MockCompany("武汉长江贸易有限公司", "91420100MA00000009", "吴刚")
        );
        for (MockCompany company : mockDb) {
            if (company.name().contains(kw)) {
                results.add(new CompanyProfile("", company.name(), company.creditCode(), company.legalPerson(),
                        "NOT_SUBMITTED", "NOT_STARTED", "NOT_STARTED", "NOT_UPLOADED"));
            }
        }
        if (results.isEmpty() && kw.length() >= 2) {
            results.add(new CompanyProfile("", kw, "91130100MA" + String.format("%08d", (int) (Math.random() * 99999999)),
                    "法定代表人", "NOT_SUBMITTED", "NOT_STARTED", "NOT_STARTED", "NOT_UPLOADED"));
        }
        return results;
    }

    public CompanyProfile getCompany(String id) {
        Company company = companyMapper.selectById(parseId(id));
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        return toCompanyProfile(company);
    }

    @Transactional
    public CompanyProfile submitCompany(CompanySubmitRequest request) {
        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>().eq(Company::getCreditCode, request.creditCode()).last("LIMIT 1"));
        if (company == null) {
            company = new Company();
            company.setId(request.id() != null && !request.id().isBlank() ? parseId(request.id()) : 10000000L + (long) (Math.random() * 90000000));
            company.setCreditCode(request.creditCode());
            company.setCertificationStatus("PENDING");
            company.setRealNameStatus("NOT_STARTED");
            company.setFaceStatus("NOT_STARTED");
            company.setSealStatus("NOT_UPLOADED");
        }
        company.setName(request.name());
        company.setLegalPersonName(request.legalPersonName());
        if (companyMapper.selectById(company.getId()) == null) {
            companyMapper.insert(company);
        } else {
            companyMapper.updateById(company);
        }
        return toCompanyProfile(companyMapper.selectById(company.getId()));
    }

    public CompanyProfile submitCertification(String id) {
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(id))
                .set(Company::getCertificationStatus, "PENDING_REVIEW"));
        return getCompany(id);
    }

    public CompanyProfile verifyRealName(VerificationRequest req) {
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(req.companyId()))
                .set(Company::getRealNameStatus, "VERIFIED"));
        return getCompany(req.companyId());
    }

    public CompanyProfile verifyFace(VerificationRequest req) {
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(req.companyId()))
                .set(Company::getFaceStatus, "VERIFIED"));
        return getCompany(req.companyId());
    }

    public SealRecord uploadSeal(SealRequest req) {
        companyMapper.update(new LambdaUpdateWrapper<Company>()
                .eq(Company::getId, parseId(req.companyId()))
                .set(Company::getSealStatus, "PENDING_REVIEW"));
        return new SealRecord(UUID.randomUUID().toString(), req.companyId(), req.fileUrl(), req.usage(), "PENDING_REVIEW");
    }

    public InviteResult createInvite(InviteRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireManager(companyId);
        String code = generateInviteCode();
        saveInvite(companyId, code, "member");
        return new InviteResult(code, req.companyId());
    }

    public InviteResult createCounterpartyInvite(InviteRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireLegal(companyId);
        String code = generateInviteCode();
        saveInvite(companyId, code, "counterparty");
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
                throw new BusinessException("仅公司法人可接受供方邀请，请先在您的公司完成法人认证");
            }
            Company userCompany = companyMapper.selectById(legalMember.getCompanyId());
            String companyName = userCompany == null ? "未知企业" : userCompany.getName();
            CounterpartyRelationEntity relation = new CounterpartyRelationEntity();
            relation.setCompanyId(invite.getCompanyId());
            relation.setCounterpartyCompanyName(companyName);
            relation.setRelationType("SUPPLIER");
            relation.setStatus("ACTIVE");
            try {
                counterpartyRelationMapper.insert(relation);
            } catch (Exception ignored) {
            }
            markInviteUsed(invite, userId);
            return new JoinResult("ACTIVE", "已绑定供方关系：" + companyName);
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
                .map(row -> {
                    String roleCode = string(row.get("roleCode"));
                    String name = string(row.get("nickname"));
                    String phone = string(row.get("phone"));
                    String displayName = name != null && !name.isBlank() && !"新用户".equals(name)
                            ? name
                            : (phone != null && !phone.isBlank() ? "用户" + phone.substring(phone.length() - 4) : "微信用户");
                    return new AuthorizationRecord(String.valueOf(row.get("id")), companyId, String.valueOf(row.get("userId")),
                            displayName, roleCode, rolePermissionService.roleText(roleCode), List.of(), string(row.get("status")),
                            phone != null ? phone : "");
                })
                .toList();
    }

    public AuthorizationRecord approveMember(String id, ApproveRequest req, String companyId) {
        long cid = parseId(companyId);
        accessControlService.requireManager(cid);
        String perms = req.customPermissions() != null && !req.customPermissions().isEmpty()
                ? toJson(req.customPermissions())
                : null;
        companyMemberMapper.update(new LambdaUpdateWrapper<CompanyMember>()
                .eq(CompanyMember::getId, parseId(id))
                .eq(CompanyMember::getCompanyId, cid)
                .set(CompanyMember::getRoleCode, req.roleCode())
                .set(CompanyMember::getCustomPermissions, perms)
                .set(CompanyMember::getStatus, "ACTIVE"));
        return new AuthorizationRecord(id, companyId, "", "", req.roleCode(),
                rolePermissionService.roleText(req.roleCode()), List.of(), "ACTIVE", "");
    }

    public void rejectMember(String id, String companyId) {
        long cid = parseId(companyId);
        accessControlService.requireManager(cid);
        companyMemberMapper.delete(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getId, parseId(id))
                .eq(CompanyMember::getCompanyId, cid)
                .eq(CompanyMember::getStatus, "PENDING"));
    }

    public void removeMember(String id, String companyId) {
        long cid = parseId(companyId);
        accessControlService.requireManager(cid);
        companyMemberMapper.delete(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getId, parseId(id))
                .eq(CompanyMember::getCompanyId, cid)
                .eq(CompanyMember::getIsLegalPerson, false));
    }

    public List<Map<String, Object>> listRoles(String companyId) {
        return roleDefMapper.selectMaps(new LambdaQueryWrapper<RoleDef>()
                .select(RoleDef::getId, RoleDef::getName, RoleDef::getPermissions)
                .eq(RoleDef::getCompanyId, parseId(companyId))
                .orderByAsc(RoleDef::getId));
    }

    public Map<String, Object> createRole(RoleRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireManager(companyId);
        RoleDef role = new RoleDef();
        role.setCompanyId(companyId);
        role.setName(req.name());
        role.setPermissions(toJson(req.permissions()));
        roleDefMapper.insert(role);
        return Map.of("id", role.getId(), "name", req.name(), "permissions", req.permissions());
    }

    public void updateRole(String id, RoleRequest req) {
        long companyId = parseId(req.companyId());
        accessControlService.requireManager(companyId);
        roleDefMapper.update(new LambdaUpdateWrapper<RoleDef>()
                .eq(RoleDef::getId, parseId(id))
                .eq(RoleDef::getCompanyId, companyId)
                .set(RoleDef::getName, req.name())
                .set(RoleDef::getPermissions, toJson(req.permissions())));
    }

    public void deleteRole(String id) {
        RoleDef role = roleDefMapper.selectById(parseId(id));
        if (role == null) {
            return;
        }
        accessControlService.requireManager(role.getCompanyId());
        roleDefMapper.deleteById(role.getId());
    }

    private void saveInvite(long companyId, String code, String type) {
        CompanyInvite invite = new CompanyInvite();
        invite.setCompanyId(companyId);
        invite.setCode(code);
        invite.setType(type);
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
                company.getLegalPersonName(), company.getCertificationStatus(), company.getRealNameStatus(),
                company.getFaceStatus(), company.getSealStatus());
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
}
