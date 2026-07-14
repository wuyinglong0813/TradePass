package com.tradepass.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.ApiResponse;
import com.tradepass.common.AuthContext;
import com.tradepass.entity.CompanyMember;
import com.tradepass.entity.SysUser;
import com.tradepass.mapper.CompanyMemberMapper;
import com.tradepass.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 鉴权拦截器：解析 token（mvp-token-{userId}）→ 写入请求级 AuthContext。
 * 当前操作企业优先取请求头 X-Company-Id，否则回退到用户第一家 ACTIVE 企业。
 * 未携带合法 token 时对受保护接口返回 401。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_PREFIX = "mvp-token-";

    private final SysUserMapper sysUserMapper;
    private final CompanyMemberMapper companyMemberMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthInterceptor(SysUserMapper sysUserMapper, CompanyMemberMapper companyMemberMapper) {
        this.sysUserMapper = sysUserMapper;
        this.companyMemberMapper = companyMemberMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = parseUserId(request.getHeader("Authorization"));
        if (userId == null) {
            writeUnauthorized(response);
            return false;
        }

        Long companyId = resolveCompanyId(userId, request.getHeader("X-Company-Id"));
        AuthContext.set(userId, companyId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private Long parseUserId(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        if (!token.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        try {
            long uid = Long.parseLong(token.substring(TOKEN_PREFIX.length()));
            return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, uid)) > 0 ? uid : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveCompanyId(long userId, String headerCompanyId) {
        // 1) 请求头指定且用户确为该企业成员 → 用它
        if (headerCompanyId != null && !headerCompanyId.isBlank()) {
            try {
                long cid = Long.parseLong(headerCompanyId);
                boolean exists = companyMemberMapper.selectCount(new LambdaQueryWrapper<CompanyMember>()
                        .eq(CompanyMember::getCompanyId, cid)
                        .eq(CompanyMember::getUserId, userId)
                        .eq(CompanyMember::getStatus, "ACTIVE")) > 0;
                if (exists) {
                    return cid;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        // 2) 回退：用户第一家 ACTIVE 企业
        try {
            List<CompanyMember> members = companyMemberMapper.selectList(new LambdaQueryWrapper<CompanyMember>()
                    .eq(CompanyMember::getUserId, userId)
                    .eq(CompanyMember::getStatus, "ACTIVE")
                    .orderByAsc(CompanyMember::getCompanyId)
                    .last("LIMIT 1"));
            return members.isEmpty() ? null : members.get(0).getCompanyId();
        } catch (Exception e) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = new ApiResponse<>(401, "未登录或登录已失效", null);
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
