package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.CompanyMember;
import com.tradepass.mapper.CompanyMemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccessControlService {
    private final CompanyMemberMapper companyMemberMapper;

    public AccessControlService(CompanyMemberMapper companyMemberMapper) {
        this.companyMemberMapper = companyMemberMapper;
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
            return isActiveMember(requested, AuthContext.userId()) ? requested : contextCompanyId;
        } catch (NumberFormatException e) {
            return contextCompanyId;
        }
    }

    public void requireManager(long companyId) {
        boolean allowed = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                .eq(CompanyMember::getCompanyId, companyId)
                .eq(CompanyMember::getUserId, AuthContext.userId())
                .eq(CompanyMember::getStatus, "ACTIVE")
                .in(CompanyMember::getRoleCode, List.of("LEGAL", "ADMIN"))) > 0;
        if (!allowed) {
            throw new BusinessException("无权操作：需要管理员或法人身份");
        }
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
}
