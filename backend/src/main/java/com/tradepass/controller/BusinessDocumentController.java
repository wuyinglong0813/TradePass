package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.entity.BusinessDocument;
import com.tradepass.service.BusinessDocumentPdfService;
import com.tradepass.service.BusinessDocumentService;
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
public class BusinessDocumentController {
    private final BusinessDocumentService businessDocumentService;
    private final BusinessDocumentPdfService businessDocumentPdfService;

    public BusinessDocumentController(BusinessDocumentService businessDocumentService,
                                      BusinessDocumentPdfService businessDocumentPdfService) {
        this.businessDocumentService = businessDocumentService;
        this.businessDocumentPdfService = businessDocumentPdfService;
    }

    @GetMapping("/document-templates")
    public ApiResponse<List<Map<String, Object>>> listTemplates(@RequestParam String type) {
        return ApiResponse.ok(businessDocumentService.listTemplates(type));
    }

    @PostMapping("/document-templates")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessDocumentService.createTemplate(body));
    }

    @PostMapping("/document-templates/{id}/delete")
    public ApiResponse<String> deleteTemplate(@PathVariable Long id) {
        return ApiResponse.ok(businessDocumentService.deleteTemplate(id));
    }

    @GetMapping("/contracts/{contractId}/documents")
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable Long contractId,
                                                                @RequestParam String type) {
        return ApiResponse.ok(businessDocumentService.listDocuments(contractId, type));
    }

    @PostMapping("/contracts/{contractId}/documents")
    public ApiResponse<Map<String, Object>> createDocument(@PathVariable Long contractId,
                                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessDocumentService.createDocument(contractId, body));
    }

    @GetMapping(value = "/trade-documents/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        BusinessDocument document = businessDocumentService.getDocument(id);
        byte[] pdf = businessDocumentPdfService.generate(document);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(businessDocumentPdfService.fileName(document), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(pdf.length)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
