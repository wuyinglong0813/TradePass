package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import com.tradepass.dto.request.AddCounterpartyRequest;
import com.tradepass.dto.request.CreateContractRequest;
import com.tradepass.dto.request.CreateOrderRequest;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.dto.response.ContractSigningPayload;
import com.tradepass.dto.response.ServiceUrlPayload;
import com.tradepass.dto.response.FileDataPayload;
import com.tradepass.dto.response.FileChunkDataPayload;
import com.tradepass.dto.response.PagePayload;
import com.tradepass.dto.response.TradeOrderPayload;
import com.tradepass.service.ContractPdfService;
import com.tradepass.service.ContractArchiveService;
import com.tradepass.service.TradeService;
import com.tradepass.service.FadadaContractSigningService;
import com.tradepass.common.AuthContext;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradeController {
    private final TradeService tradeService;
    private final ContractPdfService contractPdfService;
    private final ContractArchiveService contractArchiveService;
    private FadadaContractSigningService signingService;

    @Autowired
    public TradeController(TradeService tradeService, ContractPdfService contractPdfService,
                           ContractArchiveService contractArchiveService) {
        this.tradeService = tradeService;
        this.contractPdfService = contractPdfService;
        this.contractArchiveService = contractArchiveService;
    }

    TradeController(TradeService tradeService, ContractPdfService contractPdfService) {
        this.tradeService = tradeService;
        this.contractPdfService = contractPdfService;
        this.contractArchiveService = null;
    }

    TradeController(TradeService tradeService) {
        this(tradeService, null);
    }

    @Autowired
    void setSigningService(FadadaContractSigningService signingService) {
        this.signingService = signingService;
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
        ContractPdfFile file = contractPdfFile(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(file.data().length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.data());
    }

    @GetMapping("/contracts/{id:\\d+}/pdf-data")
    public ApiResponse<FileDataPayload> contractPdfData(@PathVariable Long id) {
        ContractPdfFile file = contractPdfFile(id);
        return ApiResponse.ok(FileDataPayload.of(file.fileName(), MediaType.APPLICATION_PDF_VALUE, file.data()));
    }

    private ContractPdfFile contractPdfFile(Long id) {
        ContractPayload contract = tradeService.getContract(id);
        ContractArchiveService.PdfPayload archived = contractArchiveService == null
                ? null : contractArchiveService.getPdf(contract, AuthContext.userId());
        byte[] pdf = archived == null ? contractPdfService.generate(contract) : archived.data();
        String fileName = archived == null ? contractPdfService.fileName(contract) : archived.fileName();
        return new ContractPdfFile(fileName, pdf);
    }

    private record ContractPdfFile(String fileName, byte[] data) {}

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
    public ApiResponse<?> approveContract(@PathVariable Long id) {
        return signingService == null
                ? ApiResponse.ok(tradeService.approveContract(id))
                : ApiResponse.ok(signingService.signUrl(id));
    }

    @GetMapping("/contracts/{id}/signing")
    public ApiResponse<ContractSigningPayload> contractSigning(@PathVariable Long id) {
        return ApiResponse.ok(signingService.current(id));
    }

    @GetMapping("/contracts/{id}/signed-preview-data")
    public ApiResponse<FileDataPayload> signedContractPreview(@PathVariable Long id) {
        FadadaContractSigningService.SignedPreview preview = signingService.signedPreview(id);
        return ApiResponse.ok(FileDataPayload.of(
                preview.fileName(), MediaType.IMAGE_PNG_VALUE, preview.data()));
    }

    @GetMapping("/contracts/{id}/signed-preview-chunk-data")
    public ApiResponse<FileChunkDataPayload> signedContractPreviewChunk(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "524288") int size) {
        if (offset < 0 || size <= 0 || size > 512 * 1024) {
            throw new BusinessException("文件分片参数不正确");
        }
        FadadaContractSigningService.SignedPreview preview = signingService.signedPreview(id);
        int totalSize = preview.data().length;
        if (offset >= totalSize) {
            throw new BusinessException("文件分片位置超出范围");
        }
        int start = (int) offset;
        int end = Math.min(totalSize, start + size);
        return ApiResponse.ok(FileChunkDataPayload.of(
                preview.fileName(), MediaType.IMAGE_PNG_VALUE,
                Arrays.copyOfRange(preview.data(), start, end),
                offset, totalSize, end == totalSize));
    }

    @PostMapping("/contracts/{id}/signing/sync")
    public ApiResponse<ContractSigningPayload> syncContractSigning(@PathVariable Long id) {
        return ApiResponse.ok(signingService.syncCurrent(id));
    }

    @PostMapping("/contracts/{id}/sign-url")
    public ApiResponse<ServiceUrlPayload> contractSignUrl(@PathVariable Long id) {
        return ApiResponse.ok(signingService.signUrl(id));
    }

    @PostMapping("/contracts/{id}/abolish-url")
    public ApiResponse<ServiceUrlPayload> contractAbolishUrl(@PathVariable Long id,
                                                             @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        return ApiResponse.ok(signingService.abolishUrl(id, reason));
    }

    @PostMapping("/contracts/{id}/reject")
    public ApiResponse<String> rejectContract(@PathVariable Long id) {
        if (signingService != null) signingService.cancelPending(id, "对方拒绝签署");
        return ApiResponse.ok(tradeService.rejectContract(id));
    }

    @PostMapping("/contracts/{id}/cancel")
    public ApiResponse<String> cancelContract(@PathVariable Long id) {
        if (signingService != null) signingService.cancelPending(id, "发起方撤回合同");
        return ApiResponse.ok(tradeService.cancelContract(id));
    }

    @PostMapping("/contracts/{id}/resubmit")
    public ApiResponse<ContractPayload> resubmitContract(@PathVariable Long id,
                                                         @Valid @RequestBody CreateContractRequest request) {
        return ApiResponse.ok(tradeService.resubmitContract(id, request));
    }

    @PostMapping("/contracts/{id}/delete")
    public ApiResponse<String> deleteContract(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.deleteContract(id));
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
