package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.Company;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserIdentityService {
    private final SysUserMapper userMapper;
    private final CompanyMapper companyMapper;

    public UserIdentityService(SysUserMapper userMapper, CompanyMapper companyMapper) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
    }

    public String currentDisplayName() {
        long userId = AuthContext.userId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) return "用户" + userId;
        String nickname = safe(user.getNickname());
        if (!nickname.isBlank()) return trim(nickname, 64);
        String phone = safe(user.getPhone());
        return phone.isBlank() ? "用户" + userId : trim(phone, 64);
    }

    public String requireCurrentVerifiedName(long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || !"VERIFIED".equals(company.getRealNameStatus())) {
            throw new BusinessException("当前企业尚未完成实名认证，不能确认销售单");
        }
        return currentDisplayName();
    }

    private String trim(String value, int maxLength) {
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
