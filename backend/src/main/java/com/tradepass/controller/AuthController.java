package com.tradepass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.ApiResponse;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.CompanyProfile;
import com.tradepass.common.TradePassDtos.CompanyRole;
import com.tradepass.common.TradePassDtos.DevUser;
import com.tradepass.common.TradePassDtos.LoginSession;
import com.tradepass.common.TradePassDtos.MePayload;
import com.tradepass.common.TradePassDtos.MemberInfo;
import com.tradepass.common.TradePassDtos.UserProfile;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final JdbcTemplate db;

    @Value("${wechat.app-id}")
    private String wechatAppId;

    @Value("${wechat.app-secret}")
    private String wechatAppSecret;

    // 角色定义
    static final Map<String, RoleDef> ROLES = Map.of(
            "LEGAL",     new RoleDef("法人",   List.of("all")),
            "ADMIN",     new RoleDef("管理员", List.of("member_manage", "auth_manage", "company_manage")),
            "SALES",     new RoleDef("销售员", List.of("supplier_view", "counterparty_manage", "order_view")),
            "PURCHASER", new RoleDef("采购员", List.of("buyer_view", "order_create")),
            "FINANCE",   new RoleDef("财务",   List.of("invoice_view", "reconciliation_view"))
    );

    record RoleDef(String text, List<String> permissions) {
    }

    /** 当前登录用户 ID + 当前操作的企业 ID */
    private volatile long currentUserId = 1;
    private volatile long currentCompanyId = 1;

    // RowMapper
    private static final RowMapper<MemberInfo> MEMBER_ROW = (rs, rowNum) -> {
        String roleCode = rs.getString("role_code");
        String status = rs.getString("status") != null ? rs.getString("status") : "ACTIVE";
        RoleDef role = (roleCode != null) ? ROLES.getOrDefault(roleCode, new RoleDef(roleCode, List.of())) : new RoleDef("GUEST", List.of());
        return new MemberInfo(
                String.valueOf(rs.getLong("user_id")),
                rs.getString("nickname"),
                rs.getString("phone"),
                roleCode,
                role.text(),
                role.permissions(),
                status
        );
    };

    private static final RowMapper<CompanyProfile> COMPANY_ROW = (rs, rowNum) -> new CompanyProfile(
            String.valueOf(rs.getLong("id")),
            rs.getString("name"),
            rs.getString("credit_code"),
            rs.getString("legal_person_name"),
            rs.getString("certification_status"),
            rs.getString("real_name_status"),
            rs.getString("face_status"),
            rs.getString("seal_status")
    );

    public AuthController(JdbcTemplate db) {
        this.db = db;
    }

    // ================================================================
    // 初始化表 + 演示数据
    // ================================================================

    @PostConstruct
    void initData() {
        // 建表（IF NOT EXISTS）
        db.execute("""
            CREATE TABLE IF NOT EXISTS sys_user (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                openid VARCHAR(64) NOT NULL UNIQUE,
                phone VARCHAR(32),
                nickname VARCHAR(64),
                status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
        """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS company (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(128) NOT NULL,
                credit_code VARCHAR(32) NOT NULL UNIQUE,
                legal_person_name VARCHAR(64) NOT NULL,
                certification_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
                real_name_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
                face_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
                seal_status VARCHAR(32) NOT NULL DEFAULT 'NOT_UPLOADED',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
        """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS company_member (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                role_code VARCHAR(64) NOT NULL,
                is_legal_person TINYINT(1) NOT NULL DEFAULT 0,
                is_administrator TINYINT(1) NOT NULL DEFAULT 0,
                status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_company_user (company_id, user_id)
            )
        """);

        // 种子数据（IGNORE 避免重复）
        db.execute("INSERT IGNORE INTO company (id, name, credit_code, legal_person_name, certification_status, real_name_status, face_status, seal_status) VALUES (1, '河北光屿行贸易有限公司', '91130100MA00000001', '满帅', 'VERIFIED', 'VERIFIED', 'VERIFIED', 'UPLOADED')");

        db.execute("INSERT IGNORE INTO sys_user (id, openid, phone, nickname) VALUES (1, 'dev-openid-001', '18800000001', '满帅')");
        db.execute("INSERT IGNORE INTO sys_user (id, openid, phone, nickname) VALUES (2, 'dev-openid-002', '18800000002', '张采购')");
        db.execute("INSERT IGNORE INTO sys_user (id, openid, phone, nickname) VALUES (3, 'dev-openid-003', '18800000003', '李销售')");
        db.execute("INSERT IGNORE INTO sys_user (id, openid, phone, nickname) VALUES (4, 'dev-openid-004', '18800000004', '王财务')");
        db.execute("INSERT IGNORE INTO sys_user (id, openid, phone, nickname) VALUES (5, 'dev-openid-005', '18800000005', '赵管理')");

        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person, is_administrator) VALUES (1, 1, 'LEGAL', 1, 0)");
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person, is_administrator) VALUES (1, 2, 'PURCHASER', 0, 0)");
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person, is_administrator) VALUES (1, 3, 'SALES', 0, 0)");
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person, is_administrator) VALUES (1, 4, 'FINANCE', 0, 0)");
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person, is_administrator) VALUES (1, 5, 'ADMIN', 0, 1)");

        // 邀请码表
        db.execute("""
            CREATE TABLE IF NOT EXISTS company_invite (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                code VARCHAR(64) NOT NULL UNIQUE,
                type VARCHAR(32) NOT NULL DEFAULT 'member',
                used TINYINT(1) NOT NULL DEFAULT 0,
                used_by BIGINT,
                expires_at DATETIME NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_code (code)
            )
        """);
        // 兼容已有的表缺少 type 列
        try { db.execute("ALTER TABLE company_invite ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'member'"); } catch (Exception ignored) {}

        // 角色定义表
        db.execute("""
            CREATE TABLE IF NOT EXISTS role_def (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                name VARCHAR(64) NOT NULL,
                permissions JSON NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_company_role (company_id, name)
            )
        """);
        // member 加 custom_permissions 字段
        try { db.execute("ALTER TABLE company_member ADD COLUMN custom_permissions JSON"); } catch (Exception ignored) {}

        // 种子角色（每个公司一份预设）
        seedRole(1, "法人",   List.of("all"));
        seedRole(1, "管理员", List.of("member_manage","auth_manage","company_manage","seal_manage"));
        seedRole(1, "销售员", List.of("supplier_view","counterparty_manage","order_view"));
        seedRole(1, "采购员", List.of("buyer_view","order_create"));
        seedRole(1, "财务",   List.of("invoice_view","reconciliation"));
        seedRole(2, "法人",   List.of("all"));
        seedRole(2, "管理员", List.of("member_manage","auth_manage","company_manage"));
        seedRole(2, "销售员", List.of("supplier_view","counterparty_manage","order_view"));
        seedRole(2, "采购员", List.of("buyer_view","order_create"));
        seedRole(2, "财务",   List.of("invoice_view","reconciliation"));
        try {
            db.execute("""
                CREATE TABLE IF NOT EXISTS counterparty_relation (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    company_id BIGINT NOT NULL,
                    counterparty_company_name VARCHAR(128) NOT NULL,
                    relation_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception ignored) {}

        // 第二家公司
        db.execute("INSERT IGNORE INTO company (id, name, credit_code, legal_person_name, certification_status, real_name_status, face_status, seal_status) VALUES (2, '上海远航进出口有限公司', '91310000MA00000002', '王海', 'VERIFIED', 'VERIFIED', 'VERIFIED', 'UPLOADED')");
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person) VALUES (2, 1, 'SALES', 0)");
    }

    // ================================================================
    // 登录
    // ================================================================

    @PostMapping("/auth/wechat-login")
    public ApiResponse<LoginSession> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        // 调微信接口拿 openid
        String openid = resolveOpenid(request.code());

        // 查用户
        List<MemberInfo> users = db.query(
                "SELECT u.id as user_id, u.nickname, u.phone, m.role_code, m.status FROM sys_user u LEFT JOIN company_member m ON u.id = m.user_id WHERE u.openid = ?",
                (rs, rowNum) -> {
                    String roleCode = rs.getString("role_code");
                    String status = rs.getString("status");
                    RoleDef role = (roleCode != null) ? ROLES.getOrDefault(roleCode, new RoleDef(roleCode, List.of())) : new RoleDef("GUEST", List.of());
                    return new MemberInfo(
                            String.valueOf(rs.getLong("user_id")),
                            rs.getString("nickname"),
                            rs.getString("phone"),
                            roleCode != null ? roleCode : "GUEST",
                            role.text(),
                            role.permissions(),
                            status != null ? status : "NONE"
                    );
                },
                openid
        );

        if (users.isEmpty()) {
            // 新用户：创建账号
            db.update("INSERT INTO sys_user (openid, nickname, phone) VALUES (?, ?, ?)", openid, "新用户", "");
            long newId = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            UserProfile user = new UserProfile(String.valueOf(newId), openid, "", "新用户", null, "GUEST");
            currentUserId = newId;
            return ApiResponse.ok(new LoginSession(tokenFor(newId), user));
        }

        MemberInfo member = users.get(0);
        currentUserId = Long.parseLong(member.userId());
        // 设置当前公司为用户第一个 ACTIVE 公司
        var cos = loadUserCompanies(currentUserId);
        currentCompanyId = cos.isEmpty() ? 0L : Long.parseLong(cos.get(0).companyId());
        UserProfile user = new UserProfile(member.userId(), openid, member.phone(), member.userName(), String.valueOf(currentCompanyId), member.roleCode());
        return ApiResponse.ok(new LoginSession(tokenFor(Long.parseLong(member.userId())), user));
    }

    @PostMapping("/auth/bind-phone")
    public ApiResponse<UserProfile> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        db.update("UPDATE sys_user SET phone = ? WHERE id = ?", request.phone(), currentUserId);
        MemberInfo member = loadMember(currentUserId, currentCompanyId);
        if (member == null) return ApiResponse.ok(null);
        return ApiResponse.ok(new UserProfile(member.userId(), "demo-openid", request.phone(), member.userName(), String.valueOf(currentCompanyId), member.roleCode()));
    }

    // ================================================================
    // 当前用户
    // ================================================================

    @GetMapping("/me")
    public ApiResponse<MePayload> me(@RequestHeader(value = "Authorization", required = false) String token) {
        MemberInfo member = loadMember(currentUserId, currentCompanyId);
        CompanyProfile company = loadCompany(currentCompanyId);
        List<CompanyRole> companies = loadUserCompanies(currentUserId);

        if (member == null) return ApiResponse.ok(null);
        UserProfile user = new UserProfile(member.userId(), "demo-openid", member.phone(), member.userName(),
                String.valueOf(currentCompanyId), member.roleCode());
        return ApiResponse.ok(new MePayload(user, company != null ? company : fallbackCompany(), member, companies));
    }

    @PostMapping("/me/switch-company")
    public ApiResponse<MePayload> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        long newCompanyId = Long.parseLong(request.companyId());
        // 验证用户确实是该公司成员
        var rows = db.queryForList("SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE'", newCompanyId, currentUserId);
        if (rows.isEmpty()) throw new BusinessException("你不是该公司成员");

        currentCompanyId = newCompanyId;
        return me(null);
    }

    @PostMapping("/me/company")
    public ApiResponse<MePayload> bindCompany(@Valid @RequestBody BindCompanyRequest request) {
        // 更新或插入公司
        db.update("INSERT INTO company (id, name, credit_code, legal_person_name, certification_status) VALUES (?, ?, ?, ?, 'PENDING') ON DUPLICATE KEY UPDATE name=VALUES(name), credit_code=VALUES(credit_code), legal_person_name=VALUES(legal_person_name)",
                parseId(request.id()), request.name(), request.creditCode(), request.legalPersonName());
        // 关联用户
        db.update("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person) VALUES (?, ?, 'LEGAL', 1)",
                parseId(request.id()), currentUserId);

        MemberInfo member = loadMember(currentUserId, currentCompanyId);
        CompanyProfile company = loadCompany(currentCompanyId);
        UserProfile user = new UserProfile(member.userId(), "demo-openid", member.phone(), member.userName(),
                company.id(), "LEGAL");
        List<CompanyRole> cos = loadUserCompanies(currentUserId);
        return ApiResponse.ok(new MePayload(user, company, member, cos));
    }

    // ================================================================
    // Dev 用户切换
    // ================================================================

    @GetMapping("/dev/users")
    public ApiResponse<List<DevUser>> listDevUsers() {
        List<DevUser> users = db.query("""
            SELECT u.id, u.nickname, u.phone, m.role_code
            FROM sys_user u
            JOIN company_member m ON u.id = m.user_id
            ORDER BY u.id
        """, (rs, rowNum) -> {
            String roleCode = rs.getString("role_code");
            RoleDef role = (roleCode != null) ? ROLES.getOrDefault(roleCode, new RoleDef(roleCode, List.of())) : new RoleDef("GUEST", List.of());
            return new DevUser(String.valueOf(rs.getLong("id")), rs.getString("nickname"), rs.getString("phone"), roleCode, role.text());
        });
        return ApiResponse.ok(users);
    }

    @PostMapping("/dev/switch-user")
    public ApiResponse<DevUser> switchUser(@Valid @RequestBody SwitchUserRequest request) {
        MemberInfo member = loadMemberAnyCompany(parseId(request.userId()));
        if (member == null) return ApiResponse.ok(null);
        currentUserId = parseId(request.userId());
        // 切换到用户的第一家公司，没有则设 0
        var cos = loadUserCompanies(currentUserId);
        currentCompanyId = cos.isEmpty() ? 0L : Long.parseLong(cos.get(0).companyId());
        return ApiResponse.ok(new DevUser(member.userId(), member.userName(), member.phone(), member.roleCode(), member.roleText()));
    }

    // ================================================================
    // DB 查询
    // ================================================================

    /** 查用户在某公司的角色 */
    private MemberInfo loadMember(long userId, long companyId) {
        List<MemberInfo> list = db.query(
                "SELECT u.id as user_id, u.nickname, u.phone, m.role_code, m.status FROM sys_user u LEFT JOIN company_member m ON u.id = m.user_id AND m.company_id = ? WHERE u.id = ?",
                MEMBER_ROW, companyId, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 查用户（不限定公司，用于登录/切换用户等场景） */
    private MemberInfo loadMemberAnyCompany(long userId) {
        List<MemberInfo> list = db.query(
                "SELECT u.id as user_id, u.nickname, u.phone, m.role_code, m.status FROM sys_user u LEFT JOIN company_member m ON u.id = m.user_id WHERE u.id = ? LIMIT 1",
                MEMBER_ROW, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    private CompanyProfile loadCompany(long companyId) {
        List<CompanyProfile> list = db.query("SELECT * FROM company WHERE id = ?", COMPANY_ROW, companyId);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<CompanyRole> loadUserCompanies(long userId) {
        return db.query(
                "SELECT c.id, c.name, m.role_code FROM company c JOIN company_member m ON c.id = m.company_id WHERE m.user_id = ? AND m.status = 'ACTIVE'",
                (rs, rowNum) -> {
                    String rc = rs.getString("role_code");
                    RoleDef role = ROLES.getOrDefault(rc, new RoleDef(rc, List.of()));
                    return new CompanyRole(String.valueOf(rs.getLong("id")), rs.getString("name"), rc, role.text());
                },
                userId);
    }

    private CompanyProfile fallbackCompany() {
        return new CompanyProfile("1", "未加入企业", "", "", "NOT_SUBMITTED", "NOT_STARTED", "NOT_STARTED", "NOT_UPLOADED");
    }

    private long parseId(String id) {
        try { return Long.parseLong(id); } catch (NumberFormatException e) { return 0; }
    }

    private void seedRole(long companyId, String name, List<String> perms) {
        try {
            String json = "[\"" + String.join("\",\"", perms) + "\"]";
            db.update("INSERT INTO role_def (company_id, name, permissions) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE permissions = VALUES(permissions)",
                    companyId, name, json);
        } catch (Exception ignored) {}
    }

    private String tokenFor(long userId) {
        return "mvp-token-" + userId;
    }

    /** 调用微信 code2Session，拿 openid */
    private String resolveOpenid(String code) {
        // dev 模式下 code 就是 openid，方便调试
        if (code.startsWith("dev-")) {
            return code;
        }
        try {
            String url = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    wechatAppId, wechatAppSecret, code);
            var client = java.net.http.HttpClient.newHttpClient();
            var httpReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .build();
            var resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(resp.body());
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                throw new BusinessException("微信登录失败: " + node.get("errmsg").asText());
            }
            return node.get("openid").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("微信登录异常: " + e.getMessage());
        }
    }

    // ---- 请求体 ----
    public record WechatLoginRequest(@NotBlank(message = "微信登录 code 不能为空") String code) {
    }

    public record BindPhoneRequest(@NotBlank(message = "手机号不能为空") String phone) {
    }

    public record BindCompanyRequest(
            @NotBlank(message = "企业 ID 不能为空") String id,
            @NotBlank(message = "企业名称不能为空") String name,
            @NotBlank(message = "统一社会信用代码不能为空") String creditCode,
            @NotBlank(message = "法人姓名不能为空") String legalPersonName,
            String certificationStatus, String realNameStatus, String faceStatus, String sealStatus
    ) {
    }

    public record SwitchUserRequest(@NotBlank(message = "用户 ID 不能为空") String userId) {
    }

    public record SwitchCompanyRequest(@NotBlank(message = "企业 ID 不能为空") String companyId) {
    }
}
