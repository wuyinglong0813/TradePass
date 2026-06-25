package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TradeController {
    private final List<TradeOrderPayload> orders = new ArrayList<>();
    private final Map<String, List<CounterpartyRelation>> counterpartiesByCompany = new LinkedHashMap<>();

    public TradeController() {
        // 公司1：河北光屿行
        counterpartiesByCompany.put("1", new ArrayList<>(List.of(
                new CounterpartyRelation("cr-001", "河北通瑞贸易有限公司", "SUPPLIER", "ACTIVE"),
                new CounterpartyRelation("cr-002", "河北逸泽昌贸易有限公司", "SUPPLIER", "ACTIVE")
        )));
        // 公司2：上海远航
        counterpartiesByCompany.put("2", new ArrayList<>(List.of(
                new CounterpartyRelation("cr-101", "上海浦发贸易有限公司", "SUPPLIER", "ACTIVE"),
                new CounterpartyRelation("cr-102", "浙江义乌商贸有限公司", "SUPPLIER", "ACTIVE"),
                new CounterpartyRelation("cr-103", "江苏南通纺织品有限公司", "SUPPLIER", "ACTIVE")
        )));

        orders.add(new TradeOrderPayload("o-001", "SALE", "河北通瑞贸易有限公司", new BigDecimal("1280000.00"), LocalDate.now().minusDays(3), "CONFIRMED"));
        orders.add(new TradeOrderPayload("o-002", "SALE", "河北通瑞贸易有限公司", new BigDecimal("650000.00"), LocalDate.now().minusDays(12), "CONFIRMED"));
        orders.add(new TradeOrderPayload("o-003", "SALE", "河北逸泽昌贸易有限公司", new BigDecimal("860000.00"), LocalDate.now().minusMonths(1), "CONFIRMED"));
        orders.add(new TradeOrderPayload("o-004", "SALE", "河北佰盛电缆科技有限公司", new BigDecimal("620000.00"), LocalDate.now().minusYears(1), "CONFIRMED"));
        orders.add(new TradeOrderPayload("o-005", "PURCHASE", "河北通瑞贸易有限公司", new BigDecimal("710000.00"), LocalDate.now().minusMonths(1), "CONFIRMED"));
    }

    @GetMapping("/orders")
    public ApiResponse<List<TradeOrderPayload>> listOrders(@RequestParam(required = false) String counterpartyName) {
        if (counterpartyName == null || counterpartyName.isBlank()) {
            return ApiResponse.ok(orders);
        }
        List<TradeOrderPayload> filtered = orders.stream()
                .filter(o -> o.counterpartyName().equals(counterpartyName))
                .toList();
        return ApiResponse.ok(filtered);
    }

    @PostMapping("/orders")
    public ApiResponse<TradeOrderPayload> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        TradeOrderPayload order = new TradeOrderPayload(UUID.randomUUID().toString(), request.direction(), request.counterpartyName(), request.amount(), request.orderDate(), "CONFIRMED");
        orders.add(order);
        return ApiResponse.ok(order);
    }

    // ---- 关联公司（供方关系，按公司区分）----

    @GetMapping("/counterparties")
    public ApiResponse<List<CounterpartyRelation>> listCounterparties(@RequestParam(defaultValue = "1") String companyId) {
        return ApiResponse.ok(counterpartiesByCompany.getOrDefault(companyId, List.of()));
    }

    @PostMapping("/counterparties")
    public ApiResponse<CounterpartyRelation> addCounterparty(@Valid @RequestBody AddCounterpartyRequest request) {
        CounterpartyRelation relation = new CounterpartyRelation("cr-" + UUID.randomUUID().toString().substring(0, 8), request.counterpartyName(), "SUPPLIER", "ACTIVE");
        counterpartiesByCompany.computeIfAbsent(request.companyId(), k -> new ArrayList<>()).add(relation);
        return ApiResponse.ok(relation);
    }

    public record CreateOrderRequest(@NotBlank String direction, @NotBlank String counterpartyName, @NotNull BigDecimal amount, @NotNull LocalDate orderDate) {}
    public record TradeOrderPayload(String id, String direction, String counterpartyName, BigDecimal amount, LocalDate orderDate, String status) {}
    public record AddCounterpartyRequest(@NotBlank String counterpartyName, @NotBlank String companyId) {}
}
