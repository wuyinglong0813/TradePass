package com.tradepass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.ApiResponse;
import com.tradepass.common.AuthContext;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
            "ADMIN",     new RoleDef("管理员", List.of("member_manage", "auth_manage", "company_manage", "contract_template")),
            "SALES",     new RoleDef("销售员", List.of("supplier_view", "counterparty_manage", "order_view", "contract_sign", "contract_view", "reconciliation")),
            "PURCHASER", new RoleDef("采购员", List.of("buyer_view", "order_create", "contract_view", "order_view")),
            "FINANCE",   new RoleDef("财务",   List.of("invoice_view", "reconciliation"))
    );

    record RoleDef(String text, List<String> permissions) {
    }

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

        // 权限定义表（系统级，所有公司共用）
        db.execute("""
            CREATE TABLE IF NOT EXISTS perm_def (
                code VARCHAR(64) PRIMARY KEY,
                label VARCHAR(64) NOT NULL,
                sort_order INT NOT NULL DEFAULT 0
            )
        """);
        seedPerm("supplier_view", "供方首页", 1);
        seedPerm("buyer_view", "需方首页", 2);
        seedPerm("counterparty_manage", "供方公司管理", 3);
        seedPerm("order_view", "订单查看", 4);
        seedPerm("order_create", "下单", 5);
        seedPerm("contract_template", "合同模板管理", 6);
        seedPerm("contract_sign", "签订合同", 7);
        seedPerm("contract_view", "合同预览", 8);
        seedPerm("invoice_view", "发票查看", 9);
        seedPerm("reconciliation", "对账情况", 10);
        seedPerm("inventory_view", "库存查看", 11);
        seedPerm("member_manage", "成员管理", 12);
        seedPerm("auth_manage", "授权管理", 13);
        seedPerm("company_manage", "企业认证", 14);
        seedPerm("seal_manage", "电子章管理", 15);

        // 合同模板表
        db.execute("""
            CREATE TABLE IF NOT EXISTS contract_template (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                name VARCHAR(256) NOT NULL,
                category VARCHAR(64),
                content TEXT,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_company (company_id)
            )
        """);
        // 种子模板
        // 模板分类表
        db.execute("""
            CREATE TABLE IF NOT EXISTS template_category (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                name VARCHAR(64) NOT NULL,
                sort_order INT NOT NULL DEFAULT 0,
                UNIQUE KEY uk_company_cat (company_id, name),
                INDEX idx_company (company_id)
            )
        """);
        seedTemplateCategory(1, "采购", 1);
        seedTemplateCategory(1, "供货", 2);
        seedTemplateCategory(1, "交易", 3);
        seedTemplateCategory(1, "物流", 4);
        seedTemplateCategory(1, "服务", 5);
        seedTemplateCategory(1, "其他", 6);

        seedContractTemplate(1, "标准采购合同模板", "采购");
        seedContractTemplate(1, "框架供货协议模板", "供货");
        seedContractTemplate(1, "单笔交易合同模板", "交易");
        seedContractTemplate(1, "物流服务合同模板", "物流");

        // 合同表
        db.execute("""
            CREATE TABLE IF NOT EXISTS trade_contract (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                counterparty_name VARCHAR(128) NOT NULL,
                name VARCHAR(256) NOT NULL,
                template_name VARCHAR(128),
                amount DECIMAL(15,2) NOT NULL DEFAULT 0,
                start_date DATE,
                end_date DATE,
                terms TEXT,
                status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                initiated_by BIGINT NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_company (company_id),
                INDEX idx_counterparty (counterparty_name)
            )
        """);
        // 种子合同
        seedContractIfEmpty(1, "河北通瑞贸易有限公司", "年度采购框架协议", "标准采购合同模板", 500000, "2026-01-01", "2026-12-31", "ACTIVE", 1);
        seedContractIfEmpty(1, "河北通瑞贸易有限公司", "Q2季度供货合同", "供货协议模板", 180000, "2026-04-01", "2026-06-30", "ACTIVE", 1);
        seedContractIfEmpty(1, "河北逸泽昌贸易有限公司", "设备采购补充协议", "单笔交易合同模板", 85000, "2026-05-15", "2026-08-15", "ACTIVE", 1);
        seedContractIfEmpty(2, "上海浦发贸易有限公司", "年度销售代理合同", "标准采购合同模板", 320000, "2026-02-01", "2027-01-31", "PENDING", 2);
        // 对方公司 发起给 我方公司 的合同（用于测试待审批流程）
        try {
            db.update("INSERT IGNORE INTO trade_contract (company_id, counterparty_name, name, template_name, amount, start_date, end_date, status, initiated_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    2, "河北光广贸易有限公司", "年度物流服务合同", "物流服务合同模板", 150000, "2026-03-01", "2027-02-28", "PENDING", 3);
        } catch (Exception ignored) {}

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
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_company_counterparty (company_id, counterparty_company_name)
                )
            """);
        } catch (Exception ignored) {}

        // 种子供方关系（仅在表为空时灌入）
        Integer cpCount = db.queryForObject("SELECT COUNT(1) FROM counterparty_relation", Integer.class);
        if (cpCount == null || cpCount == 0) {
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (1, '河北通瑞贸易有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (1, '河北逸泽昌贸易有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (2, '上海浦发贸易有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (2, '浙江义乌商贸有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (2, '江苏南通纺织品有限公司', 'SUPPLIER', 'ACTIVE')");
        }

        // 第二家公司
        db.execute("INSERT IGNORE INTO company (id, name, credit_code, legal_person_name, certification_status, real_name_status, face_status, seal_status) VALUES (2, '上海远航进出口有限公司', '91310000MA00000002', '王海', 'VERIFIED', 'VERIFIED', 'VERIFIED', 'UPLOADED')");
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person) VALUES (2, 1, 'SALES', 0)");

        // 订单表（销售/采购订单，排行与订单列表共用同一数据源）
        db.execute("""
            CREATE TABLE IF NOT EXISTS trade_order (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                direction VARCHAR(16) NOT NULL,
                counterparty_name VARCHAR(128) NOT NULL,
                order_no VARCHAR(64),
                amount DECIMAL(18,2) NOT NULL,
                order_date DATE NOT NULL,
                status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_company_dir (company_id, direction)
            )
        """);
        // 兼容已有表：追加 order_no 列
        try { db.execute("ALTER TABLE trade_order ADD COLUMN order_no VARCHAR(64) AFTER counterparty_name"); } catch (Exception ignored) {}
        // 种子订单（仅在表为空时灌入，避免重复）
        Integer orderCount = db.queryForObject("SELECT COUNT(1) FROM trade_order", Integer.class);
        if (orderCount == null || orderCount == 0) {
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (1,'SALE','河北通瑞贸易有限公司','ORD-001',1280000.00, DATE_SUB(CURDATE(), INTERVAL 3 DAY))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (1,'SALE','河北逸泽昌贸易有限公司','ORD-002',860000.00, DATE_SUB(CURDATE(), INTERVAL 2 MONTH))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (1,'SALE','河北佰盛电缆科技有限公司','ORD-003',620000.00, DATE_SUB(CURDATE(), INTERVAL 1 YEAR))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (1,'PURCHASE','河北通瑞贸易有限公司','ORD-004',960000.00, DATE_SUB(CURDATE(), INTERVAL 5 DAY))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (1,'PURCHASE','河北逸泽昌贸易有限公司','ORD-005',710000.00, DATE_SUB(CURDATE(), INTERVAL 1 MONTH))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (2,'SALE','上海浦发贸易有限公司','ORD-006',2560000.00, DATE_SUB(CURDATE(), INTERVAL 2 DAY))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (2,'SALE','浙江义乌商贸有限公司','ORD-007',1890000.00, DATE_SUB(CURDATE(), INTERVAL 7 DAY))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (2,'SALE','江苏南通纺织品有限公司','ORD-008',1430000.00, DATE_SUB(CURDATE(), INTERVAL 1 MONTH))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (2,'PURCHASE','上海浦发贸易有限公司','ORD-009',980000.00, DATE_SUB(CURDATE(), INTERVAL 10 DAY))");
            db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, order_no, amount, order_date) VALUES (2,'PURCHASE','浙江义乌商贸有限公司','ORD-010',810000.00, DATE_SUB(CURDATE(), INTERVAL 1 MONTH))");
        }
    }

    // ================================================================
    // 登录
    // ================================================================

    @PostMapping("/auth/wechat-login")
    public ApiResponse<LoginSession> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        // 调微信接口拿 openid
        String openid = resolveOpenid(request.code());

        // 若前端传来手机号动态令牌（手机号快捷登录），用 access_token 换取真实手机号
        String resolvedPhone = request.phone();
        if (request.phoneCode() != null && !request.phoneCode().isBlank()) {
            resolvedPhone = resolvePhoneByCode(request.phoneCode());
        }
        final String phoneToUse = resolvedPhone;

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
            String nick = request.nickName() != null && !request.nickName().isBlank() ? request.nickName() : "微信用户";
            String phone = phoneToUse != null ? phoneToUse : "";
            db.update("INSERT INTO sys_user (openid, nickname, phone) VALUES (?, ?, ?)", openid, nick, phone);
            long newId = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            UserProfile user = new UserProfile(String.valueOf(newId), openid, phone, nick, null, "GUEST");
            return ApiResponse.ok(new LoginSession(tokenFor(newId), user));
        }

        MemberInfo member = users.get(0);
        long userId = Long.parseLong(member.userId());
        // 更新昵称和手机号
        if (request.nickName() != null && !request.nickName().isBlank()) {
            db.update("UPDATE sys_user SET nickname = ? WHERE id = ?", request.nickName(), userId);
        }
        if (phoneToUse != null && !phoneToUse.isBlank()) {
            db.update("UPDATE sys_user SET phone = ? WHERE id = ?", phoneToUse, userId);
        }
        // 当前公司 = 用户第一家 ACTIVE 企业
        var cos = loadUserCompanies(userId);
        String currentCompanyId = cos.isEmpty() ? null : cos.get(0).companyId();
        String shownPhone = (phoneToUse != null && !phoneToUse.isBlank()) ? phoneToUse : member.phone();
        UserProfile user = new UserProfile(member.userId(), openid, shownPhone, member.userName(), currentCompanyId, member.roleCode());
        return ApiResponse.ok(new LoginSession(tokenFor(userId), user));
    }

    @PostMapping("/auth/bind-phone")
    public ApiResponse<UserProfile> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        long userId = AuthContext.userId();
        Long companyId = AuthContext.companyId();
        db.update("UPDATE sys_user SET phone = ? WHERE id = ?", request.phone(), userId);
        MemberInfo member = loadMember(userId, companyId == null ? 0L : companyId);
        if (member == null) return ApiResponse.ok(null);
        return ApiResponse.ok(new UserProfile(member.userId(), "demo-openid", request.phone(), member.userName(),
                companyId == null ? null : String.valueOf(companyId), member.roleCode()));
    }

    // ================================================================
    // 当前用户
    // ================================================================

    @GetMapping("/me")
    public ApiResponse<MePayload> me() {
        return ApiResponse.ok(buildMe(AuthContext.userId(), AuthContext.companyId()));
    }

    /** 统一待办中心：汇总当前用户在当前企业下的审批 / 认证等待办事项 */
    /** 获取所有权限定义列表（系统级，供角色管理页使用） */
    @GetMapping("/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.ok(db.queryForList("SELECT code, label FROM perm_def ORDER BY sort_order"));
    }

    @GetMapping("/me/todos")
    public ApiResponse<List<TodoItem>> myTodos() {
        long userId = AuthContext.userId();
        Long companyId = AuthContext.companyId();
        List<TodoItem> todos = new ArrayList<>();
        if (companyId == null) {
            return ApiResponse.ok(todos);
        }
        boolean manager = !db.queryForList(
                "SELECT 1 FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE' AND role_code IN ('LEGAL','ADMIN')",
                companyId, userId).isEmpty();
        if (manager) {
            Integer pending = db.queryForObject(
                    "SELECT COUNT(1) FROM company_member WHERE company_id = ? AND status = 'PENDING'", Integer.class, companyId);
            if (pending != null && pending > 0) {
                todos.add(new TodoItem("APPROVAL", "成员待审批", pending + " 位同事申请加入企业", pending, "auth-manage"));
            }
            var co = db.queryForList("SELECT certification_status FROM company WHERE id = ?", companyId);
            if (!co.isEmpty()) {
                String cs = (String) co.get(0).get("certification_status");
                if (cs != null && !"VERIFIED".equals(cs)) {
                    todos.add(new TodoItem("CERT", "企业认证待完成", "完成认证后可使用全部签约能力", 1, "company-cert"));
                }
            }
            // 待审批合同（对方发起给我方的）
            String companyName = co.isEmpty() ? null : (String) co.get(0).get("name");
            if (companyName != null) {
                Integer pendingContracts = db.queryForObject(
                        "SELECT COUNT(1) FROM trade_contract WHERE counterparty_name = ? AND status = 'PENDING'", Integer.class, companyName);
                if (pendingContracts != null && pendingContracts > 0) {
                    todos.add(new TodoItem("CONTRACT", "合同待审批", pendingContracts + " 份合同等待你方签署", pendingContracts, "contract-approval"));
                }
            }
        }
        return ApiResponse.ok(todos);
    }

    public record TodoItem(String type, String title, String desc, int count, String target) {
    }

    private MePayload buildMe(long userId, Long companyId) {
        List<CompanyRole> companies = loadUserCompanies(userId);
        // 当前企业：上下文给定且在用户企业内，否则取第一家
        Long effectiveCompanyId = companyId;
        if (effectiveCompanyId == null && !companies.isEmpty()) {
            effectiveCompanyId = Long.parseLong(companies.get(0).companyId());
        }
        MemberInfo member = loadMember(userId, effectiveCompanyId == null ? 0L : effectiveCompanyId);
        if (member == null) {
            // 用户存在但还未加入任何企业
            var urows = db.queryForList("SELECT nickname, phone FROM sys_user WHERE id = ?", userId);
            String nick = urows.isEmpty() ? "微信用户" : (String) urows.get(0).get("nickname");
            String phone = urows.isEmpty() ? "" : (String) urows.get(0).get("phone");
            UserProfile u = new UserProfile(String.valueOf(userId), "demo-openid", phone, nick, null, "GUEST");
            return new MePayload(u, fallbackCompany(), null, companies);
        }
        CompanyProfile company = effectiveCompanyId == null ? null : loadCompany(effectiveCompanyId);
        UserProfile user = new UserProfile(member.userId(), "demo-openid", member.phone(), member.userName(),
                effectiveCompanyId == null ? null : String.valueOf(effectiveCompanyId), member.roleCode());
        return new MePayload(user, company != null ? company : fallbackCompany(), member, companies);
    }

    @PostMapping("/me/switch-company")
    public ApiResponse<MePayload> switchCompany(@Valid @RequestBody SwitchCompanyRequest request) {
        long userId = AuthContext.userId();
        long newCompanyId = Long.parseLong(request.companyId());
        // 验证用户确实是该公司成员
        var rows = db.queryForList("SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE'", newCompanyId, userId);
        if (rows.isEmpty()) throw new BusinessException("你不是该公司成员");
        return ApiResponse.ok(buildMe(userId, newCompanyId));
    }

    @PostMapping("/me/company")
    public ApiResponse<MePayload> bindCompany(@Valid @RequestBody BindCompanyRequest request) {
        long userId = AuthContext.userId();
        long companyId = parseId(request.id());
        // 更新或插入公司
        db.update("INSERT INTO company (id, name, credit_code, legal_person_name, certification_status) VALUES (?, ?, ?, ?, 'PENDING') ON DUPLICATE KEY UPDATE name=VALUES(name), credit_code=VALUES(credit_code), legal_person_name=VALUES(legal_person_name)",
                companyId, request.name(), request.creditCode(), request.legalPersonName());
        // 关联用户为法人
        db.update("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person, status) VALUES (?, ?, 'LEGAL', 1, 'ACTIVE')",
                companyId, userId);
        // 新建/绑定后即以该企业为当前企业
        return ApiResponse.ok(buildMe(userId, companyId));
    }

    // ================================================================
    // Dev 用户切换（开发专用，生产应整体下线）
    // ================================================================

    @GetMapping("/dev/users")
    public ApiResponse<List<DevUser>> listDevUsers() {
        List<DevUser> users = db.query("""
            SELECT u.id, u.nickname, u.phone, m.role_code
            FROM sys_user u
            LEFT JOIN company_member m ON u.id = m.user_id
            ORDER BY u.id
        """, (rs, rowNum) -> {
            String roleCode = rs.getString("role_code");
            RoleDef role = (roleCode != null) ? ROLES.getOrDefault(roleCode, new RoleDef(roleCode, List.of())) : new RoleDef("GUEST", List.of());
            return new DevUser(String.valueOf(rs.getLong("id")), rs.getString("nickname"), rs.getString("phone"), roleCode, role.text());
        });
        return ApiResponse.ok(users);
    }

    /** Dev 切换用户：返回该用户的 token，前端换 token 即完成切换（无服务端全局状态） */
    @PostMapping("/dev/switch-user")
    public ApiResponse<LoginSession> switchUser(@Valid @RequestBody SwitchUserRequest request) {
        long userId = parseId(request.userId());
        MemberInfo member = loadMemberAnyCompany(userId);
        if (member == null) return ApiResponse.ok(null);
        var cos = loadUserCompanies(userId);
        String currentCompanyId = cos.isEmpty() ? null : cos.get(0).companyId();
        UserProfile user = new UserProfile(member.userId(), "dev-openid", member.phone(), member.userName(), currentCompanyId, member.roleCode());
        return ApiResponse.ok(new LoginSession(tokenFor(userId), user));
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

    private void seedPerm(String code, String label, int sortOrder) {
        try {
            db.update("INSERT IGNORE INTO perm_def (code, label, sort_order) VALUES (?, ?, ?)", code, label, sortOrder);
        } catch (Exception ignored) {}
    }

    private void seedTemplateCategory(long companyId, String name, int sortOrder) {
        try {
            db.update("INSERT IGNORE INTO template_category (company_id, name, sort_order) VALUES (?, ?, ?)", companyId, name, sortOrder);
        } catch (Exception ignored) {}
    }

    private void seedContractTemplate(long companyId, String name, String category) {
        try {
            db.update("INSERT IGNORE INTO contract_template (company_id, name, category) VALUES (?, ?, ?)", companyId, name, category);
        } catch (Exception ignored) {}
    }

    private void seedContractIfEmpty(long companyId, String counterpartyName, String name, String templateName, double amount, String startDate, String endDate, String status, long initiatedBy) {
        try {
            Integer cnt = db.queryForObject("SELECT COUNT(1) FROM trade_contract WHERE company_id = ?", Integer.class, companyId);
            if (cnt == null || cnt == 0) {
                db.update("INSERT INTO trade_contract (company_id, counterparty_name, name, template_name, amount, start_date, end_date, status, initiated_by) VALUES (?,?,?,?,?,?,?,?,?)",
                        companyId, counterpartyName, name, templateName, amount, startDate, endDate, status, initiatedBy);
            }
        } catch (Exception ignored) {}
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

    /** 用手机号动态令牌 code 调微信接口换取真实手机号 */
    private String resolvePhoneByCode(String phoneCode) {
        try {
            String token = getAccessToken();
            String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + token;
            var mapper = new ObjectMapper();
            String body = mapper.writeValueAsString(Map.of("code", phoneCode));
            var client = java.net.http.HttpClient.newHttpClient();
            var httpReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            var node = mapper.readTree(resp.body());
            if (node.path("errcode").asInt(-1) != 0) {
                throw new BusinessException("获取手机号失败: " + node.path("errmsg").asText());
            }
            return node.path("phone_info").path("phoneNumber").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取手机号异常: " + e.getMessage());
        }
    }

    /** 获取小程序 access_token（演示用，未做缓存；生产需缓存并按 expires_in 刷新） */
    private String getAccessToken() {
        try {
            if (wechatAppSecret == null || wechatAppSecret.isBlank()) {
                throw new BusinessException("未配置 WECHAT_APP_SECRET，无法获取 access_token");
            }
            String url = String.format(
                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                    wechatAppId, wechatAppSecret);
            var client = java.net.http.HttpClient.newHttpClient();
            var httpReq = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            var resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            var node = new ObjectMapper().readTree(resp.body());
            if (!node.has("access_token")) {
                throw new BusinessException("获取 access_token 失败: " + resp.body());
            }
            return node.get("access_token").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取 access_token 异常: " + e.getMessage());
        }
    }

    // ---- 请求体 ----
    public record WechatLoginRequest(@NotBlank(message = "微信登录 code 不能为空") String code, String nickName, String avatarUrl, String phone, String phoneCode) {
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
