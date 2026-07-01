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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    public record CreateOrderRequest(@NotBlank String direction, @NotBlank String counterpartyName, @NotNull BigDecimal amount, @NotNull LocalDate orderDate) {}
    public record TradeOrderPayload(String id, String direction, String counterpartyName, BigDecimal amount, LocalDate orderDate, String status) {}
    public record AddCounterpartyRequest(@NotBlank String counterpartyName) {}
}
