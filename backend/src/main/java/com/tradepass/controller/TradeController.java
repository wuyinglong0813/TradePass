package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradeController {

    private final JdbcTemplate db;

    private static final RowMapper<TradeOrderPayload> ORDER_ROW = (rs, n) -> new TradeOrderPayload(
            String.valueOf(rs.getLong("id")),
            rs.getString("direction"),
            rs.getString("counterparty_name"),
            rs.getBigDecimal("amount"),
            rs.getDate("order_date").toLocalDate(),
            rs.getString("status")
    );

    private static final RowMapper<CounterpartyRelation> COUNTERPARTY_ROW = (rs, n) -> new CounterpartyRelation(
            String.valueOf(rs.getLong("id")),
            rs.getString("counterparty_company_name"),
            rs.getString("relation_type"),
            rs.getString("status")
    );

    public TradeController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/orders")
    public ApiResponse<List<TradeOrderPayload>> listOrders(@RequestParam(required = false) String counterpartyName) {
        long companyId = AuthContext.requireCompanyId();
        List<TradeOrderPayload> list;
        if (counterpartyName == null || counterpartyName.isBlank()) {
            list = db.query("SELECT * FROM trade_order WHERE company_id = ? ORDER BY order_date DESC", ORDER_ROW, companyId);
        } else {
            list = db.query("SELECT * FROM trade_order WHERE company_id = ? AND counterparty_name = ? ORDER BY order_date DESC",
                    ORDER_ROW, companyId, counterpartyName);
        }
        return ApiResponse.ok(list);
    }

    @PostMapping("/orders")
    public ApiResponse<TradeOrderPayload> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        long companyId = AuthContext.requireCompanyId();
        db.update("INSERT INTO trade_order (company_id, direction, counterparty_name, amount, order_date, status) VALUES (?, ?, ?, ?, ?, 'CONFIRMED')",
                companyId, request.direction(), request.counterpartyName(), request.amount(), request.orderDate());
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.ok(new TradeOrderPayload(String.valueOf(id), request.direction(), request.counterpartyName(), request.amount(), request.orderDate(), "CONFIRMED"));
    }

    // ---- 关联公司（供方关系，持久化至 counterparty_relation 表）----

    @GetMapping("/counterparties")
    public ApiResponse<List<CounterpartyRelation>> listCounterparties(@RequestParam(required = false) String companyId) {
        long cid = resolveCompanyId(companyId);
        List<CounterpartyRelation> list = db.query(
                "SELECT id, counterparty_company_name, relation_type, status FROM counterparty_relation WHERE company_id = ? AND status = 'ACTIVE' ORDER BY id",
                COUNTERPARTY_ROW, cid);
        return ApiResponse.ok(list);
    }

    @PostMapping("/counterparties")
    public ApiResponse<CounterpartyRelation> addCounterparty(@Valid @RequestBody AddCounterpartyRequest request) {
        long companyId = AuthContext.requireCompanyId();
        requireLegal(companyId);
        db.update("INSERT INTO counterparty_relation (company_id, counterparty_company_name, relation_type, status) VALUES (?, ?, 'SUPPLIER', 'ACTIVE')",
                companyId, request.counterpartyName());
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.ok(new CounterpartyRelation(String.valueOf(id), request.counterpartyName(), "SUPPLIER", "ACTIVE"));
    }

    private long resolveCompanyId(String companyId) {
        long contextCid = AuthContext.requireCompanyId();
        if (companyId == null || companyId.isBlank()) {
            return contextCid;
        }
        try {
            long requested = Long.parseLong(companyId);
            var rows = db.queryForList(
                    "SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE'",
                    requested, AuthContext.userId());
            return rows.isEmpty() ? contextCid : requested;
        } catch (NumberFormatException e) {
            return contextCid;
        }
    }

    private void requireLegal(long companyId) {
        var rows = db.queryForList(
                "SELECT id FROM company_member WHERE company_id = ? AND user_id = ? AND status = 'ACTIVE' AND role_code = 'LEGAL'",
                companyId, AuthContext.userId());
        if (rows.isEmpty()) {
            throw new BusinessException("无权操作：仅法人可执行");
        }
    }

    // ---- 模板分类 ----

    @GetMapping("/contract-template-categories")
    public ApiResponse<List<Map<String, Object>>> listTemplateCategories() {
        long companyId = AuthContext.requireCompanyId();
        return ApiResponse.ok(db.queryForList(
                "SELECT id, name FROM template_category WHERE company_id = ? ORDER BY sort_order", companyId));
    }

    @PostMapping("/contract-template-categories")
    public ApiResponse<Map<String, Object>> addCategory(@RequestBody Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        String name = ((String) body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) throw new BusinessException("分类名不能为空");
        db.update("INSERT IGNORE INTO template_category (company_id, name) VALUES (?, ?)", companyId, name);
        return ApiResponse.ok(db.queryForList("SELECT id, name FROM template_category WHERE company_id = ? AND name = ?", companyId, name).get(0));
    }

    @PostMapping("/contract-template-categories/{id}/delete")
    public ApiResponse<String> deleteCategory(@PathVariable Long id) {
        db.update("DELETE FROM template_category WHERE id = ?", id);
        return ApiResponse.ok("已删除");
    }

    // ---- 合同模板 ----

    @GetMapping("/contract-templates")
    public ApiResponse<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        long companyId = AuthContext.requireCompanyId();
        StringBuilder sql = new StringBuilder("SELECT t.id, t.name, t.category, t.content, t.created_by, t.updated_by, t.created_at, t.updated_at, u1.nickname AS created_by_name, u2.nickname AS updated_by_name FROM contract_template t LEFT JOIN sys_user u1 ON t.created_by = u1.id LEFT JOIN sys_user u2 ON t.updated_by = u2.id WHERE t.company_id = ?");
        List<Object> params = new java.util.ArrayList<>();
        params.add(companyId);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND name LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }
        sql.append(" ORDER BY updated_at DESC");
        return ApiResponse.ok(db.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/contract-templates/{id}")
    public ApiResponse<Map<String, Object>> getTemplate(@PathVariable Long id) {
        var rows = db.queryForList("SELECT t.id, t.name, t.category, t.content, t.created_by, t.updated_by, t.created_at, t.updated_at, u1.nickname AS created_by_name, u2.nickname AS updated_by_name FROM contract_template t LEFT JOIN sys_user u1 ON t.created_by = u1.id LEFT JOIN sys_user u2 ON t.updated_by = u2.id WHERE t.id = ?", id);
        if (rows.isEmpty()) throw new BusinessException("模板不存在");
        return ApiResponse.ok(rows.get(0));
    }

    @PostMapping("/contract-templates")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        String name = (String) body.getOrDefault("name", "");
        String category = (String) body.getOrDefault("category", "");
        String content = (String) body.getOrDefault("content", "");
        if (name.isBlank()) throw new BusinessException("模板名称不能为空");
        long userId = AuthContext.userId();
        db.update("INSERT INTO contract_template (company_id, name, category, content, created_by) VALUES (?,?,?,?,?)",
                companyId, name, category, content, userId);
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.ok(db.queryForList("SELECT id, name, category, content, created_by, updated_by, created_at, updated_at FROM contract_template WHERE id = ?", id).get(0));
    }

    @PostMapping("/contract-templates/{id}")
    public ApiResponse<Map<String, Object>> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "");
        String category = (String) body.getOrDefault("category", "");
        String content = (String) body.getOrDefault("content", "");
        if (name.isBlank()) throw new BusinessException("模板名称不能为空");
        long userId = AuthContext.userId();
        db.update("UPDATE contract_template SET name = ?, category = ?, content = ?, updated_by = ? WHERE id = ?", name, category, content, userId, id);
        return ApiResponse.ok(db.queryForList("SELECT id, name, category, content, created_by, updated_by, created_at, updated_at FROM contract_template WHERE id = ?", id).get(0));
    }

    @PostMapping("/contract-templates/{id}/delete")
    public ApiResponse<String> deleteTemplate(@PathVariable Long id) {
        db.update("DELETE FROM contract_template WHERE id = ?", id);
        return ApiResponse.ok("已删除");
    }

    // ---- 合同 ----

    @GetMapping("/contracts/{id:\\d+}")
    public ApiResponse<ContractPayload> getContract(@PathVariable Long id) {
        var rows = db.queryForList("SELECT * FROM trade_contract WHERE id = ?", id);
        if (rows.isEmpty()) throw new BusinessException("合同不存在");
        var r = rows.get(0);
        return ApiResponse.ok(new ContractPayload(
                String.valueOf((Long) r.get("id")),
                (String) r.get("counterparty_name"),
                (String) r.get("name"),
                (String) r.get("template_name"),
                (java.math.BigDecimal) r.get("amount"),
                r.get("start_date") != null ? r.get("start_date").toString() : null,
                r.get("end_date") != null ? r.get("end_date").toString() : null,
                (String) r.get("terms"),
                (String) r.get("status"),
                String.valueOf((Long) r.get("initiated_by")),
                r.get("created_at") != null ? r.get("created_at").toString() : null
        ));
    }

    @GetMapping("/contracts")
    public ApiResponse<List<ContractPayload>> listContracts(@RequestParam(required = false) String counterpartyName) {
        long companyId = AuthContext.requireCompanyId();
        String sql = "SELECT * FROM trade_contract WHERE company_id = ?";
        Object[] params;
        if (counterpartyName != null && !counterpartyName.isBlank()) {
            sql += " AND counterparty_name = ?";
            params = new Object[]{companyId, counterpartyName};
        } else {
            params = new Object[]{companyId};
        }
        sql += " ORDER BY created_at DESC";
        var rows = db.queryForList(sql, params);
        List<ContractPayload> list = rows.stream().map(r -> new ContractPayload(
                String.valueOf((Long) r.get("id")),
                (String) r.get("counterparty_name"),
                (String) r.get("name"),
                (String) r.get("template_name"),
                (java.math.BigDecimal) r.get("amount"),
                r.get("start_date") != null ? r.get("start_date").toString() : null,
                r.get("end_date") != null ? r.get("end_date").toString() : null,
                (String) r.get("terms"),
                (String) r.get("status"),
                String.valueOf((Long) r.get("initiated_by")),
                r.get("created_at") != null ? r.get("created_at").toString() : null
        )).toList();
        return ApiResponse.ok(list);
    }

    /** 发起签订合同 */
    @PostMapping("/contracts")
    public ApiResponse<ContractPayload> createContract(@Valid @RequestBody CreateContractRequest request) {
        long companyId = AuthContext.requireCompanyId();
        long userId = AuthContext.userId();
        db.update("INSERT INTO trade_contract (company_id, counterparty_name, name, template_name, amount, start_date, end_date, terms, status, initiated_by) VALUES (?,?,?,?,?,?,?,?,'PENDING',?)",
                companyId, request.counterpartyName(), request.name(), request.templateName(),
                request.amount(), request.startDate(), request.endDate(), request.terms(), userId);
        long id = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.ok(new ContractPayload(String.valueOf(id), request.counterpartyName(), request.name(),
                request.templateName(), request.amount(), request.startDate(), request.endDate(), request.terms(),
                "PENDING", String.valueOf(userId), LocalDate.now().toString()));
    }

    /** 审批通过合同（仅对方公司可操作） */
    @PostMapping("/contracts/{id}/approve")
    public ApiResponse<String> approveContract(@PathVariable Long id) {
        var rows = db.queryForList("SELECT * FROM trade_contract WHERE id = ? AND status = 'PENDING'", id);
        if (rows.isEmpty()) throw new BusinessException("合同不存在或状态不是待审批");
        db.update("UPDATE trade_contract SET status = 'ACTIVE' WHERE id = ?", id);
        return ApiResponse.ok("合同已签署生效");
    }

    /** 拒绝合同 */
    @PostMapping("/contracts/{id}/reject")
    public ApiResponse<String> rejectContract(@PathVariable Long id) {
        var rows = db.queryForList("SELECT * FROM trade_contract WHERE id = ? AND status = 'PENDING'", id);
        if (rows.isEmpty()) throw new BusinessException("合同不存在或状态不是待审批");
        db.update("UPDATE trade_contract SET status = 'REJECTED' WHERE id = ?", id);
        return ApiResponse.ok("合同已拒绝");
    }

    /** 我发起的合同列表（查看我发起的所有合同进度） */
    @GetMapping("/contracts/initiated")
    public ApiResponse<List<ContractPayload>> myInitiatedContracts() {
        long userId = AuthContext.userId();
        var rows = db.queryForList("SELECT * FROM trade_contract WHERE initiated_by = ? ORDER BY created_at DESC", userId);
        List<ContractPayload> list = rows.stream().map(r -> new ContractPayload(
                String.valueOf((Long) r.get("id")),
                (String) r.get("counterparty_name"),
                (String) r.get("name"),
                (String) r.get("template_name"),
                (java.math.BigDecimal) r.get("amount"),
                r.get("start_date") != null ? r.get("start_date").toString() : null,
                r.get("end_date") != null ? r.get("end_date").toString() : null,
                (String) r.get("terms"),
                (String) r.get("status"),
                String.valueOf((Long) r.get("initiated_by")),
                r.get("created_at") != null ? r.get("created_at").toString() : null
        )).toList();
        return ApiResponse.ok(list);
    }

    /** 待审批的合同列表（我是对方公司，看别人发给我的合同） */
    @GetMapping("/contracts/pending")
    public ApiResponse<List<ContractPayload>> pendingContracts() {
        Long companyId = AuthContext.companyId();
        if (companyId == null) return ApiResponse.ok(List.of());
        String companyName = db.queryForObject("SELECT name FROM company WHERE id = ?", String.class, companyId);
        if (companyName == null) return ApiResponse.ok(List.of());
        var rows = db.queryForList("SELECT * FROM trade_contract WHERE counterparty_name = ? AND status = 'PENDING' ORDER BY created_at DESC", companyName);
        List<ContractPayload> list = rows.stream().map(r -> new ContractPayload(
                String.valueOf((Long) r.get("id")),
                (String) r.get("counterparty_name"),
                (String) r.get("name"),
                (String) r.get("template_name"),
                (java.math.BigDecimal) r.get("amount"),
                r.get("start_date") != null ? r.get("start_date").toString() : null,
                r.get("end_date") != null ? r.get("end_date").toString() : null,
                (String) r.get("terms"),
                (String) r.get("status"),
                String.valueOf((Long) r.get("initiated_by")),
                r.get("created_at") != null ? r.get("created_at").toString() : null
        )).toList();
        return ApiResponse.ok(list);
    }

    public record CreateOrderRequest(@NotBlank String direction, @NotBlank String counterpartyName, @NotNull BigDecimal amount, @NotNull LocalDate orderDate) {}
    public record TradeOrderPayload(String id, String direction, String counterpartyName, BigDecimal amount, LocalDate orderDate, String status) {}
    public record AddCounterpartyRequest(@NotBlank String counterpartyName) {}
    public record CreateContractRequest(@NotBlank String counterpartyName, @NotBlank String name, String templateName, @NotNull BigDecimal amount, String startDate, String endDate, String terms) {}
    public record ContractPayload(String id, String counterpartyName, String name, String templateName, BigDecimal amount, String startDate, String endDate, String terms, String status, String initiatedBy, String createdAt) {}
}
