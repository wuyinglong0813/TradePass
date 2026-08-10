package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.BusinessException;
import com.tradepass.service.SalesOrderInventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InventoryController {
    private final SalesOrderInventoryService inventoryService;

    public InventoryController(SalesOrderInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/trade-documents/{id}")
    public ApiResponse<Map<String, Object>> salesOrderDetail(@PathVariable Long id) {
        return ApiResponse.ok(inventoryService.documentDetail(id));
    }

    @PostMapping("/sales-orders/{id}/receive")
    public ApiResponse<Map<String, Object>> receive(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(inventoryService.receive(id,
                String.valueOf(body.getOrDefault("decision", "")),
                longValue(body.get("warehouseId")),
                String.valueOf(body.getOrDefault("reason", ""))));
    }

    @GetMapping("/warehouses")
    public ApiResponse<List<Map<String, Object>>> warehouses() {
        return ApiResponse.ok(inventoryService.listWarehouses());
    }

    @PostMapping("/warehouses")
    public ApiResponse<Map<String, Object>> createWarehouse(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(inventoryService.createWarehouse(
                String.valueOf(body.getOrDefault("name", "")),
                String.valueOf(body.getOrDefault("address", ""))));
    }

    @GetMapping("/inventory/overview")
    public ApiResponse<Map<String, Object>> inventoryOverview() {
        return ApiResponse.ok(inventoryService.inventoryOverview());
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException("仓库 ID 格式不正确");
        }
    }
}
