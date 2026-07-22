package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.RoleDef;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.mapper.RoleDefMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class AccessControlService {
    public record EffectiveRole(String code, String name, List<String> permissions) {
    }
    public enum CompanyProfileAccess {
        SENSITIVE_OWNER,
        MEMBER,
        COUNTERPARTY
    }

    private final CompanyMemberMapper companyMemberMapper;
    private final CounterpartyRelationMapper counterpartyRelationMapper;
    private final RoleDefMapper roleDefMapper;
    private final RolePermissionService rolePermissionService;
    private final ObjectMapper objectMapper;

    public AccessControlService(CompanyMemberMapper companyMemberMapper,
                                CounterpartyRelationMapper counterpartyRelationMapper,
                                RoleDefMapper roleDefMapper,
                                RolePermissionService rolePermissionService,
                                ObjectMapper objectMapper) {
        this.companyMemberMapper = companyMemberMapper;
        this.counterpartyRelationMapper = counterpartyRelationMapper;
        this.roleDefMapper = roleDefMapper;
        this.rolePermissionService = rolePermissionService;
        this.objectMapper = objectMapper;
    }

    public boolean isActiveMember(long companyId, long userId) {
        return companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, userId)
                .eq(CompanyMember::getStatus, "ACTIVE")) > 0;
    }

    public Long resolveCompanyId(String companyId) {
        long contextCompanyId = AuthContext.requireCompanyId();
        if (companyId == null || companyId.isBlank()) {
            return contextCompanyId;
        }
        try {
            long requested = Long.parseLong(companyId);
            if (!isActiveMember(requested, AuthContext.userId())) {
                throw new BusinessException("你不是该企业的有效成员");
            }
            return requested;
        } catch (NumberFormatException e) {
            throw new BusinessException("企业 ID 格式不正确");
        }
    }

    public void requireManager(long companyId) {
        requireAnyPermission(companyId, "member_manage", "auth_manage");
    }

    public void requireLegal(long companyId) {
        boolean allowed = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, AuthContext.userId())
                .eq(CompanyMember::getStatus, "ACTIVE")
                .eq(CompanyMember::getRoleCode, "LEGAL")) > 0;
        if (!allowed) {
            throw new BusinessException("无权操作：仅法人可执行");
        }
    }

    public void requireMemberOrClaim(long companyId) {
        boolean allowed = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, AuthContext.userId())
                .in(CompanyMember::getStatus, List.of("ACTIVE", "PENDING"))) > 0;
        if (!allowed) {
            throw new BusinessException("无权访问该企业");
        }
    }

    /**
     * 企业敏感资料仅对法人/候选法人开放；普通成员和有效合作方只能获得脱敏资料。
     */
    public CompanyProfileAccess requireCompanyProfileAccess(long targetCompanyId) {
        long userId = AuthContext.userId();
        boolean sensitiveOwner = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, targetCompanyId)
                .eq(CompanyMember::getUserId, userId)
                .and(q -> q.eq(CompanyMember::getRoleCode, "LEGAL")
                        .eq(CompanyMember::getStatus, "ACTIVE")
                        .or(claim -> claim.eq(CompanyMember::getRoleCode, "LEGAL_CANDIDATE")
                                .eq(CompanyMember::getStatus, "PENDING")))) > 0;
        if (sensitiveOwner) {
            return CompanyProfileAccess.SENSITIVE_OWNER;
        }

        if (isActiveMember(targetCompanyId, userId)) {
            return CompanyProfileAccess.MEMBER;
        }

        Long currentCompanyId = AuthContext.companyId();
        if (currentCompanyId != null
                && isActiveMember(currentCompanyId, userId)
                && counterpartyRelationMapper.countActiveBetween(currentCompanyId, targetCompanyId) > 0) {
            return CompanyProfileAccess.COUNTERPARTY;
        }
        throw new BusinessException("无权访问企业敏感资料");
    }

    public void requireLegalOrClaim(long companyId) {
        boolean allowed = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, AuthContext.userId())
                .and(q -> q.eq(CompanyMember::getRoleCode, "LEGAL")
                        .eq(CompanyMember::getStatus, "ACTIVE")
                        .or(nested -> nested.eq(CompanyMember::getRoleCode, "LEGAL_CANDIDATE")
                                .eq(CompanyMember::getStatus, "PENDING")))) > 0;
        if (!allowed) {
            throw new BusinessException("无权执行企业认领认证");
        }
    }

    public void requirePermission(long companyId, String permission) {
        if (!hasPermission(companyId, permission)) {
            throw new BusinessException("无权操作：缺少权限 " + permission);
        }
    }

    public void requireAnyPermission(long companyId, String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(companyId, permission)) {
                return;
            }
        }
        throw new BusinessException("无权执行该操作");
    }

    public boolean hasPermission(long companyId, String permission) {
        return effectiveRole(companyId, AuthContext.userId()).permissions().stream()
                .anyMatch(value -> "all".equals(value) || permission.equals(value));
    }

    public EffectiveRole effectiveRole(long companyId, long userId) {
        CompanyMember member = companyMemberMapper.selectOne(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, userId)
                .eq(CompanyMember::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (member == null) {
            RolePermissionService.RoleDef guest = rolePermissionService.role("GUEST");
            return new EffectiveRole("GUEST", guest.text(), guest.permissions());
        }

        Set<String> effective = new HashSet<>();
        RoleDef role = roleDefMapper.selectOne(new LambdaQueryWrapper<RoleDef>()
                .eq(RoleDef::getCompanyId, companyId)
                .eq(RoleDef::getCode, member.getRoleCode())
                .last("LIMIT 1"));
        if (role != null) {
            effective.addAll(parsePermissions(role.getPermissions()));
        } else {
            effective.addAll(rolePermissionService.role(member.getRoleCode()).permissions());
        }
        effective.addAll(parsePermissions(member.getCustomPermissions()));
        List<String> permissions = new ArrayList<>(effective);
        Collections.sort(permissions);
        String name = role == null ? rolePermissionService.roleText(member.getRoleCode()) : role.getName();
        return new EffectiveRole(member.getRoleCode(), name, List.copyOf(permissions));
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
}
