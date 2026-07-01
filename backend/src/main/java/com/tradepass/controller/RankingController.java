package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.AuthContext;
import com.tradepass.common.TradePassDtos.HomePayload;
import com.tradepass.common.TradePassDtos.RankingItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RankingController {

    private final JdbcTemplate db;

    public RankingController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/home/supplier")
    public ApiResponse<HomePayload> supplierHome(
            @RequestParam(defaultValue = "total") String period,
            @RequestParam(required = false) String companyId
    ) {
        long cid = resolveCompanyId(companyId);
        return ApiResponse.ok(new HomePayload(
                String.valueOf(cid), loadCompanyName(cid), "SUPPLIER", "我是供应商",
                List.of("total", "year", "month"),
                rank("SALE", period, cid)
        ));
    }

    @GetMapping("/home/buyer")
    public ApiResponse<HomePayload> buyerHome(
            @RequestParam(defaultValue = "total") String period,
            @RequestParam(required = false) String companyId
    ) {
        long cid = resolveCompanyId(companyId);
        return ApiResponse.ok(new HomePayload(
                String.valueOf(cid), loadCompanyName(cid), "BUYER", "我是采购商",
                List.of("total", "year", "month"),
                rank("PURCHASE", period, cid)
        ));
    }

    @GetMapping("/rankings/sales")
    public ApiResponse<List<RankingItem>> salesRanking(@RequestParam(defaultValue = "total") String period, @RequestParam(required = false) String companyId) {
        return ApiResponse.ok(rank("SALE", period, resolveCompanyId(companyId)));
    }

    @GetMapping("/rankings/purchase")
    public ApiResponse<List<RankingItem>> purchaseRanking(@RequestParam(defaultValue = "total") String period, @RequestParam(required = false) String companyId) {
        return ApiResponse.ok(rank("PURCHASE", period, resolveCompanyId(companyId)));
    }

    /** 优先用上下文当前企业；前端显式传 companyId 时校验是否本人企业（防越权查看他人数据） */
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

    private String loadCompanyName(long id) {
        try {
            var rows = db.queryForList("SELECT name FROM company WHERE id = ?", id);
            if (!rows.isEmpty()) return (String) rows.get(0).get("name");
        } catch (Exception ignored) {}
        return "未知企业";
    }

    private String periodClause(String period) {
        return switch (period == null ? "total" : period.toLowerCase()) {
            case "year" -> " AND YEAR(order_date) = YEAR(CURDATE()) ";
            case "month" -> " AND YEAR(order_date) = YEAR(CURDATE()) AND MONTH(order_date) = MONTH(CURDATE()) ";
            default -> "";
        };
    }

    private List<RankingItem> rank(String direction, String period, long companyId) {
        String sql = "SELECT counterparty_name, SUM(amount) AS total_amount, COUNT(1) AS order_count "
                + "FROM trade_order WHERE company_id = ? AND direction = ? "
                + periodClause(period)
                + " GROUP BY counterparty_name ORDER BY total_amount DESC";
        var rows = db.queryForList(sql, companyId, direction);
        List<RankingItem> ranked = new ArrayList<>();
        int rank = 1;
        for (var r : rows) {
            ranked.add(new RankingItem(
                    rank++,
                    (String) r.get("counterparty_name"),
                    (java.math.BigDecimal) r.get("total_amount"),
                    ((Number) r.get("order_count")).intValue(),
                    "FLAT"
            ));
        }
        return ranked;
    }
}
