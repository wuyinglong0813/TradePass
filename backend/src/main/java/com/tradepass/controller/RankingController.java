package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.HomePayload;
import com.tradepass.common.TradePassDtos.RankingItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class RankingController {

    private final JdbcTemplate db;
    private final List<TradeOrder> orders = seedOrders();

    public RankingController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/home/supplier")
    public ApiResponse<HomePayload> supplierHome(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "total") String period,
            @RequestParam(defaultValue = "1") String companyId
    ) {
        CompanyInfo info = loadCompany(companyId);
        return ApiResponse.ok(new HomePayload(
                companyId,
                info.name(),
                "SUPPLIER",
                "我是供应商",
                List.of("total", "year", "month"),
                rank(TradeDirection.SALE, PeriodType.from(period), companyId)
        ));
    }

    @GetMapping("/home/buyer")
    public ApiResponse<HomePayload> buyerHome(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "total") String period,
            @RequestParam(defaultValue = "1") String companyId
    ) {
        CompanyInfo info = loadCompany(companyId);
        return ApiResponse.ok(new HomePayload(
                companyId,
                info.name(),
                "BUYER",
                "我是采购商",
                List.of("total", "year", "month"),
                rank(TradeDirection.PURCHASE, PeriodType.from(period), companyId)
        ));
    }

    private CompanyInfo loadCompany(String id) {
        try {
            var rows = db.queryForList("SELECT name FROM company WHERE id = ?", Long.parseLong(id));
            if (!rows.isEmpty()) {
                return new CompanyInfo((String) rows.get(0).get("name"));
            }
        } catch (Exception ignored) {}
        return new CompanyInfo("未知企业");
    }

    private record CompanyInfo(String name) {}

    @GetMapping("/rankings/sales")
    public ApiResponse<List<RankingItem>> salesRanking(@RequestParam(defaultValue = "total") String period, @RequestParam(defaultValue = "1") String companyId) {
        return ApiResponse.ok(rank(TradeDirection.SALE, PeriodType.from(period), companyId));
    }

    @GetMapping("/rankings/purchase")
    public ApiResponse<List<RankingItem>> purchaseRanking(@RequestParam(defaultValue = "total") String period, @RequestParam(defaultValue = "1") String companyId) {
        return ApiResponse.ok(rank(TradeDirection.PURCHASE, PeriodType.from(period), companyId));
    }

    private List<RankingItem> rank(TradeDirection direction, PeriodType period, String companyId) {
        LocalDate now = LocalDate.now();
        Map<String, List<TradeOrder>> grouped = orders.stream()
                .filter(order -> order.companyId().equals(companyId))
                .filter(order -> order.direction() == direction)
                .filter(order -> matchesPeriod(order.orderDate(), now, period))
                .collect(Collectors.groupingBy(TradeOrder::counterpartyName));

        List<RankingItem> sorted = grouped.entrySet().stream()
                .map(entry -> new RankingItem(
                        0,
                        entry.getKey(),
                        entry.getValue().stream().map(TradeOrder::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().size(),
                        "FLAT"
                ))
                .sorted(Comparator.comparing(RankingItem::amount).reversed())
                .toList();

        List<RankingItem> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            RankingItem item = sorted.get(i);
            ranked.add(new RankingItem(i + 1, item.counterpartyName(), item.amount(), item.orderCount(), item.trend()));
        }
        return ranked;
    }

    private boolean matchesPeriod(LocalDate orderDate, LocalDate now, PeriodType period) {
        return switch (period) {
            case TOTAL -> true;
            case YEAR -> orderDate.getYear() == now.getYear();
            case MONTH -> YearMonth.from(orderDate).equals(YearMonth.from(now));
        };
    }

    private List<TradeOrder> seedOrders() {
        LocalDate now = LocalDate.now();
        return List.of(
                // 公司1 的销售/采购数据
                new TradeOrder("1", TradeDirection.SALE, "河北通瑞贸易有限公司", new BigDecimal("1280000.00"), now.minusDays(3)),
                new TradeOrder("1", TradeDirection.SALE, "河北逸泽昌贸易有限公司", new BigDecimal("860000.00"), now.minusMonths(2)),
                new TradeOrder("1", TradeDirection.SALE, "河北佰盛电缆科技有限公司", new BigDecimal("620000.00"), now.minusYears(1)),
                new TradeOrder("1", TradeDirection.PURCHASE, "河北通瑞贸易有限公司", new BigDecimal("960000.00"), now.minusDays(5)),
                new TradeOrder("1", TradeDirection.PURCHASE, "河北逸泽昌贸易有限公司", new BigDecimal("710000.00"), now.minusMonths(1)),
                // 公司2 的销售/采购数据
                new TradeOrder("2", TradeDirection.SALE, "上海浦发贸易有限公司", new BigDecimal("2560000.00"), now.minusDays(2)),
                new TradeOrder("2", TradeDirection.SALE, "浙江义乌商贸有限公司", new BigDecimal("1890000.00"), now.minusDays(7)),
                new TradeOrder("2", TradeDirection.SALE, "江苏南通纺织品有限公司", new BigDecimal("1430000.00"), now.minusMonths(1)),
                new TradeOrder("2", TradeDirection.PURCHASE, "上海浦发贸易有限公司", new BigDecimal("980000.00"), now.minusDays(10)),
                new TradeOrder("2", TradeDirection.PURCHASE, "浙江义乌商贸有限公司", new BigDecimal("810000.00"), now.minusMonths(1))
        );
    }

    private enum TradeDirection {
        SALE, PURCHASE
    }

    private enum PeriodType {
        TOTAL, YEAR, MONTH;

        static PeriodType from(String value) {
            if (value == null || value.isBlank()) {
                return TOTAL;
            }
            return switch (value.toLowerCase()) {
                case "year" -> YEAR;
                case "month" -> MONTH;
                default -> TOTAL;
            };
        }
    }

    private record TradeOrder(String companyId, TradeDirection direction, String counterpartyName, BigDecimal amount, LocalDate orderDate) {
    }
}
