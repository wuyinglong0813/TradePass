package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.Company;
import com.tradepass.entity.SysUser;
import com.tradepass.entity.FadadaUserIdentity;
import com.tradepass.mapper.FadadaUserIdentityMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserIdentityService {
    private final SysUserMapper userMapper;
    private final CompanyMapper companyMapper;
    private FadadaUserIdentityMapper identityMapper;

    public UserIdentityService(SysUserMapper userMapper, CompanyMapper companyMapper) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
    }

    @Autowired
    void setIdentityMapper(FadadaUserIdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
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
        if (identityMapper == null) return currentDisplayName();
        FadadaUserIdentity identity = identityMapper.selectOne(new LambdaQueryWrapper<FadadaUserIdentity>()
                .eq(FadadaUserIdentity::getUserId, AuthContext.userId()).last("LIMIT 1"));
        if (identity == null || !"VERIFIED".equals(identity.getLocalStatus())
                || identity.getVerifiedName() == null || identity.getVerifiedName().isBlank()) {
            throw new BusinessException("请先完成个人认证，再确认业务单据");
        }
        return trim(identity.getVerifiedName(), 64);
    }

    private String trim(String value, int maxLength) {
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
