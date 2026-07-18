package com.tradepass.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仅开发环境启用的本地演示数据初始化。生产环境完全由 Flyway 管理数据库结构。
 */
@Component
@ConditionalOnProperty(name = "tradepass.demo-data.enabled", havingValue = "true")
public class DatabaseInitializer {
    private final JdbcTemplate db;

    public DatabaseInitializer(JdbcTemplate db) {
        this.db = db;
    }

    @PostConstruct
    void initData() {
        createTables();
        seedBaseData();
        seedPermissions();
        seedTemplateCategories();
        seedContractTemplates();
        seedBusinessDocumentTemplates();
        seedContracts();
        seedRoles();
        seedCounterparties();
        seedOrders();
    }

    private void createTables() {
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
                custom_permissions JSON,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_company_user (company_id, user_id)
            )
        """);
        try { db.execute("ALTER TABLE company_member ADD COLUMN custom_permissions JSON"); } catch (Exception ignored) {}
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
        try { db.execute("ALTER TABLE company_invite ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'member'"); } catch (Exception ignored) {}
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
        db.execute("""
            CREATE TABLE IF NOT EXISTS perm_def (
                code VARCHAR(64) PRIMARY KEY,
                label VARCHAR(64) NOT NULL,
                sort_order INT NOT NULL DEFAULT 0
            )
        """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS contract_template (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                name VARCHAR(256) NOT NULL,
                category VARCHAR(64),
                content TEXT,
                created_by BIGINT,
                updated_by BIGINT,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_company (company_id)
            )
        """);
        try { db.execute("ALTER TABLE contract_template ADD COLUMN created_by BIGINT"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE contract_template ADD COLUMN updated_by BIGINT"); } catch (Exception ignored) {}
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
        try { db.execute("ALTER TABLE trade_order ADD COLUMN order_no VARCHAR(64) AFTER counterparty_name"); } catch (Exception ignored) {}
        db.execute("""
            CREATE TABLE IF NOT EXISTS business_document_template (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                document_type VARCHAR(32) NOT NULL,
                name VARCHAR(256) NOT NULL,
                content TEXT,
                source_file_name VARCHAR(256),
                created_by BIGINT,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_company_type (company_id, document_type)
            )
        """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS business_document (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                company_id BIGINT NOT NULL,
                contract_id BIGINT NOT NULL,
                document_type VARCHAR(32) NOT NULL,
                document_no VARCHAR(64) NOT NULL,
                template_id BIGINT NOT NULL,
                template_name VARCHAR(256) NOT NULL,
                content LONGTEXT,
                created_by BIGINT,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_document_no (document_no),
                INDEX idx_contract_type (company_id, contract_id, document_type)
            )
        """);
    }

    private void seedBaseData() {
        db.execute("INSERT IGNORE INTO company (id, name, credit_code, legal_person_name, certification_status, real_name_status, face_status, seal_status) VALUES (1, '河北光屿行贸易有限公司', '91130100MA00000001', '满帅', 'VERIFIED', 'VERIFIED', 'VERIFIED', 'UPLOADED')");
        db.execute("INSERT IGNORE INTO company (id, name, credit_code, legal_person_name, certification_status, real_name_status, face_status, seal_status) VALUES (2, '上海远航进出口有限公司', '91310000MA00000002', '王海', 'VERIFIED', 'VERIFIED', 'VERIFIED', 'UPLOADED')");
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
        db.execute("INSERT IGNORE INTO company_member (company_id, user_id, role_code, is_legal_person) VALUES (2, 1, 'SALES', 0)");
    }

    private void seedPermissions() {
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
    }

    private void seedTemplateCategories() {
        seedTemplateCategory(1, "采购", 1);
        seedTemplateCategory(1, "供货", 2);
        seedTemplateCategory(1, "交易", 3);
        seedTemplateCategory(1, "物流", 4);
        seedTemplateCategory(1, "服务", 5);
        seedTemplateCategory(1, "其他", 6);
    }

    private void seedContractTemplates() {
        seedContractTemplate(1, "标准采购合同模板", "采购");
        seedContractTemplate(1, "框架供货协议模板", "供货");
        seedContractTemplate(1, "单笔交易合同模板", "交易");
        seedContractTemplate(1, "物流服务合同模板", "物流");
    }

    private void seedBusinessDocumentTemplates() {
        seedBusinessDocumentTemplate(1, "SALES_ORDER", "标准销售单模板",
                "{\"columns\":[\"序号\",\"品名\",\"规格\",\"单位\",\"数量\",\"单价\",\"金额\",\"备注\"],\"blankRows\":8}");
        seedBusinessDocumentTemplate(1, "DELIVERY_NOTE", "标准送货单模板",
                "{\"columns\":[\"序号\",\"品名\",\"规格\",\"数量\",\"单位\",\"备注\"],\"blankRows\":10}");
    }

    private void seedContracts() {
        db.update("""
                UPDATE trade_contract
                SET contract_no = CONCAT('HT-LEGACY-', LPAD(id, 8, '0'))
                WHERE contract_no IS NULL OR contract_no = ''
                """);
        seedContractIfEmpty(1, "河北通瑞贸易有限公司", "年度采购框架协议", "标准采购合同模板", 500000, "2026-01-01", "2026-12-31", "ACTIVE", 1);
        seedContractIfEmpty(1, "河北通瑞贸易有限公司", "Q2季度供货合同", "供货协议模板", 180000, "2026-04-01", "2026-06-30", "ACTIVE", 1);
        seedContractIfEmpty(1, "河北逸泽昌贸易有限公司", "设备采购补充协议", "单笔交易合同模板", 85000, "2026-05-15", "2026-08-15", "ACTIVE", 1);
        seedContractIfEmpty(2, "上海浦发贸易有限公司", "年度销售代理合同", "标准采购合同模板", 320000, "2026-02-01", "2027-01-31", "PENDING", 2);
        seedContractIfMissing(2, "DEMO-HT-2-002", "河北光广贸易有限公司", "年度物流服务合同",
                "物流服务合同模板", 150000, "2026-03-01", "2027-02-28", "PENDING", 3);
    }

    private void seedRoles() {
        List.of(1L, 2L).forEach(companyId -> {
            seedRole(companyId, "法人", List.of("all"));
            seedRole(companyId, "管理员", companyId == 1 ? List.of("member_manage", "auth_manage", "company_manage", "seal_manage") : List.of("member_manage", "auth_manage", "company_manage"));
            seedRole(companyId, "销售员", List.of("supplier_view", "counterparty_manage", "order_view", "order_create",
                    "contract_sign", "contract_view", "reconciliation"));
            seedRole(companyId, "采购员", List.of("buyer_view", "order_create", "contract_view", "order_view",
                    "contract_sign", "reconciliation"));
            seedRole(companyId, "财务", List.of("invoice_view", "reconciliation"));
        });
    }

    private void seedCounterparties() {
        Integer count = db.queryForObject("SELECT COUNT(1) FROM counterparty_relation", Integer.class);
        if (count == null || count == 0) {
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (1, '河北通瑞贸易有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (1, '河北逸泽昌贸易有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (2, '上海浦发贸易有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (2, '浙江义乌商贸有限公司', 'SUPPLIER', 'ACTIVE')");
            db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (2, '江苏南通纺织品有限公司', 'SUPPLIER', 'ACTIVE')");
        }
    }

    private void seedOrders() {
        Integer count = db.queryForObject("SELECT COUNT(1) FROM trade_order", Integer.class);
        if (count != null && count > 0) {
            return;
        }
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

    private void seedPerm(String code, String label, int sortOrder) {
        try { db.update("INSERT IGNORE INTO perm_def (code, label, sort_order) VALUES (?, ?, ?)", code, label, sortOrder); } catch (Exception ignored) {}
    }

    private void seedTemplateCategory(long companyId, String name, int sortOrder) {
        try { db.update("INSERT IGNORE INTO template_category (company_id, name, sort_order) VALUES (?, ?, ?)", companyId, name, sortOrder); } catch (Exception ignored) {}
    }

    private void seedContractTemplate(long companyId, String name, String category) {
        try {
            Integer count = db.queryForObject(
                    "SELECT COUNT(1) FROM contract_template WHERE company_id = ? AND name = ? AND category = ?",
                    Integer.class, companyId, name, category);
            if (count == null || count == 0) {
                db.update("INSERT INTO contract_template (company_id, name, category) VALUES (?, ?, ?)", companyId, name, category);
            }
        } catch (Exception ignored) {}
    }

    private void seedBusinessDocumentTemplate(long companyId, String type, String name, String content) {
        Integer count = db.queryForObject("""
                SELECT COUNT(1) FROM business_document_template
                WHERE company_id = ? AND document_type = ? AND name = ?
                """, Integer.class, companyId, type, name);
        if (count == null || count == 0) {
            db.update("""
                    INSERT INTO business_document_template
                    (company_id, document_type, name, content, created_by)
                    VALUES (?, ?, ?, ?, 1)
                    """, companyId, type, name, content);
        }
    }

    private void seedContractIfEmpty(long companyId, String counterpartyName, String name, String templateName, double amount, String startDate, String endDate, String status, long initiatedBy) {
        try {
            Integer count = db.queryForObject("SELECT COUNT(1) FROM trade_contract WHERE company_id = ?", Integer.class, companyId);
            if (count == null || count == 0) {
                db.update("INSERT INTO trade_contract (contract_no, company_id, counterparty_name, name, template_name, amount, start_date, end_date, status, initiated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        "DEMO-HT-" + companyId + "-001", companyId, counterpartyName, name, templateName,
                        amount, startDate, endDate, status, initiatedBy);
            }
        } catch (Exception ignored) {}
    }

    private void seedContractIfMissing(long companyId, String contractNo, String counterpartyName, String name,
                                       String templateName, double amount, String startDate, String endDate,
                                       String status, long initiatedBy) {
        try {
            Integer count = db.queryForObject(
                    "SELECT COUNT(1) FROM trade_contract WHERE company_id = ? AND counterparty_name = ? AND name = ?",
                    Integer.class, companyId, counterpartyName, name);
            if (count == null || count == 0) {
                db.update("INSERT INTO trade_contract (contract_no, company_id, counterparty_name, name, template_name, amount, start_date, end_date, status, initiated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        contractNo, companyId, counterpartyName, name, templateName, amount, startDate, endDate,
                        status, initiatedBy);
            }
        } catch (Exception ignored) {}
    }

    private void seedRole(long companyId, String name, List<String> permissions) {
        try {
            String json = "[\"" + String.join("\",\"", permissions) + "\"]";
            db.update("INSERT INTO role_def (company_id, code, name, permissions) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE code = VALUES(code), permissions = VALUES(permissions)",
                    companyId, roleCode(name), name, json);
        } catch (Exception ignored) {}
    }

    private String roleCode(String name) {
        return switch (name) {
            case "法人" -> "LEGAL";
            case "管理员" -> "ADMIN";
            case "销售员" -> "SALES";
            case "采购员" -> "PURCHASER";
            case "财务" -> "FINANCE";
            default -> "CUSTOM";
        };
    }
}
