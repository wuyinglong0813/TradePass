package com.tradepass.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.ApiResponse;
import com.tradepass.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate db;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthInterceptor(JdbcTemplate db) {
        this.db = db;
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
            // 校验用户确实存在
            Integer cnt = db.queryForObject("SELECT COUNT(1) FROM sys_user WHERE id = ?", Integer.class, uid);
            return (cnt != null && cnt > 0) ? uid : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveCompanyId(long userId, String headerCompanyId) {
        // 1) 请求头指定且用户确为该企业成员 → 用它
        if (headerCompanyId != null && !headerCompanyId.isBlank()) {
            try {
                long cid = Long.parseLong(headerCompanyId);
                List<?> rows = db.queryForList(
                        "SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE'",
                        cid, userId);
                if (!rows.isEmpty()) {
                    return cid;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        // 2) 回退：用户第一家 ACTIVE 企业
        try {
            List<Long> ids = db.query(
                    "SELECT company_id FROM company_member WHERE user_id = ? AND status = 'ACTIVE' ORDER BY company_id LIMIT 1",
                    (rs, n) -> rs.getLong("company_id"), userId);
            return ids.isEmpty() ? null : ids.get(0);
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
