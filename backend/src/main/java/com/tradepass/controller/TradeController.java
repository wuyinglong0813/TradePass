package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import com.tradepass.dto.request.AddCounterpartyRequest;
import com.tradepass.dto.request.CreateContractRequest;
import com.tradepass.dto.request.CreateOrderRequest;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.dto.response.TradeOrderPayload;
import com.tradepass.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradeController {
    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @GetMapping("/orders")
    public ApiResponse<List<TradeOrderPayload>> listOrders(@RequestParam(required = false) String counterpartyName) {
        return ApiResponse.ok(tradeService.listOrders(counterpartyName));
    }

    @PostMapping("/orders")
    public ApiResponse<TradeOrderPayload> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(tradeService.createOrder(request));
    }

    @GetMapping("/counterparties")
    public ApiResponse<List<CounterpartyRelation>> listCounterparties(@RequestParam(required = false) String companyId) {
        return ApiResponse.ok(tradeService.listCounterparties(companyId));
    }

    @PostMapping("/counterparties")
    public ApiResponse<CounterpartyRelation> addCounterparty(@Valid @RequestBody AddCounterpartyRequest request) {
        return ApiResponse.ok(tradeService.addCounterparty(request));
    }

    @GetMapping("/contract-template-categories")
    public ApiResponse<List<Map<String, Object>>> listTemplateCategories() {
        return ApiResponse.ok(tradeService.listTemplateCategories());
    }

    @PostMapping("/contract-template-categories")
    public ApiResponse<Map<String, Object>> addCategory(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tradeService.addCategory(body));
    }

    @PostMapping("/contract-template-categories/{id}/delete")
    public ApiResponse<String> deleteCategory(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.deleteCategory(id));
    }

    @GetMapping("/contract-templates")
    public ApiResponse<List<Map<String, Object>>> listTemplates(@RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String category) {
        return ApiResponse.ok(tradeService.listTemplates(keyword, category));
    }

    @GetMapping("/contract-templates/{id}")
    public ApiResponse<Map<String, Object>> getTemplate(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.getTemplate(id));
    }

    @PostMapping("/contract-templates")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tradeService.createTemplate(body));
    }

    @PostMapping("/contract-templates/{id}")
    public ApiResponse<Map<String, Object>> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tradeService.updateTemplate(id, body));
    }

    @PostMapping("/contract-templates/{id}/delete")
    public ApiResponse<String> deleteTemplate(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.deleteTemplate(id));
    }

    @GetMapping("/contracts/{id:\\d+}")
    public ApiResponse<ContractPayload> getContract(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.getContract(id));
    }

    @GetMapping("/contracts")
    public ApiResponse<List<ContractPayload>> listContracts(@RequestParam(required = false) String counterpartyName) {
        return ApiResponse.ok(tradeService.listContracts(counterpartyName));
    }

    @PostMapping("/contracts")
    public ApiResponse<ContractPayload> createContract(@Valid @RequestBody CreateContractRequest request) {
        return ApiResponse.ok(tradeService.createContract(request));
    }

    @PostMapping("/contracts/{id}/approve")
    public ApiResponse<String> approveContract(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.approveContract(id));
    }

    @PostMapping("/contracts/{id}/reject")
    public ApiResponse<String> rejectContract(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.rejectContract(id));
    }

    @GetMapping("/contracts/initiated")
    public ApiResponse<List<ContractPayload>> myInitiatedContracts() {
        return ApiResponse.ok(tradeService.myInitiatedContracts());
    }

    @GetMapping("/contracts/pending")
    public ApiResponse<List<ContractPayload>> pendingContracts() {
        return ApiResponse.ok(tradeService.pendingContracts());
    }
}
