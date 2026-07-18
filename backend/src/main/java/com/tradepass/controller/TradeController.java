package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import com.tradepass.dto.request.AddCounterpartyRequest;
import com.tradepass.dto.request.CreateContractRequest;
import com.tradepass.dto.request.CreateOrderRequest;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.dto.response.PagePayload;
import com.tradepass.dto.response.TradeOrderPayload;
import com.tradepass.service.ContractPdfService;
import com.tradepass.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradeController {
    private final TradeService tradeService;
    private final ContractPdfService contractPdfService;

    @Autowired
    public TradeController(TradeService tradeService, ContractPdfService contractPdfService) {
        this.tradeService = tradeService;
        this.contractPdfService = contractPdfService;
    }

    TradeController(TradeService tradeService) {
        this(tradeService, null);
    }

    @GetMapping("/orders")
    public ApiResponse<PagePayload<TradeOrderPayload>> listOrders(@RequestParam(required = false) String counterpartyName,
                                                                  @RequestParam(required = false) String direction,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tradeService.pageOrders(counterpartyName, direction, page, size));
    }

    @GetMapping("/orders/summary")
    public ApiResponse<Map<String, Object>> orderSummary(@RequestParam(required = false) String counterpartyName,
                                                         @RequestParam(required = false) String direction) {
        return ApiResponse.ok(tradeService.orderSummary(counterpartyName, direction));
    }

    @GetMapping("/orders/monthly-summary")
    public ApiResponse<List<Map<String, Object>>> monthlyOrderSummary(@RequestParam String counterpartyName,
                                                                      @RequestParam String direction) {
        return ApiResponse.ok(tradeService.monthlyOrderSummary(counterpartyName, direction));
    }

    @PostMapping("/orders")
    public ApiResponse<TradeOrderPayload> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(tradeService.createOrder(request));
    }

    @GetMapping("/counterparties")
    public ApiResponse<List<CounterpartyRelation>> listCounterparties(@RequestParam(required = false) String companyId,
                                                                      @RequestParam(defaultValue = "buyer") String role) {
        return ApiResponse.ok(tradeService.listCounterparties(companyId, role));
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
    public ApiResponse<PagePayload<Map<String, Object>>> listTemplates(@RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) String category,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tradeService.pageTemplates(keyword, category, page, size));
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

    @GetMapping(value = "/contracts/{id:\\d+}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadContractPdf(@PathVariable Long id) {
        ContractPayload contract = tradeService.getContract(id);
        byte[] pdf = contractPdfService.generate(contract);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(contractPdfService.fileName(contract), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(pdf.length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/contracts/summary")
    public ApiResponse<Map<String, Object>> contractSummary() {
        return ApiResponse.ok(tradeService.contractSummary());
    }

    @GetMapping("/contracts")
    public ApiResponse<PagePayload<ContractPayload>> listContracts(@RequestParam(required = false) String counterpartyName,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tradeService.pageContracts(counterpartyName, status, page, size));
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
