package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.dto.response.FileDataPayload;
import com.tradepass.service.ReconciliationPdfService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reconciliation-accounts")
public class ReconciliationPdfController {
    private final ReconciliationPdfService pdfService;

    public ReconciliationPdfController(ReconciliationPdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping(value = "/{counterpartyCompanyId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long counterpartyCompanyId,
                                      @RequestParam(defaultValue = "false") boolean download) {
        ReconciliationPdfService.PdfPayload file = pdfService.generate(counterpartyCompanyId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.data().length)
                .body(file.data());
    }

    @GetMapping("/{counterpartyCompanyId}/pdf-data")
    public ApiResponse<FileDataPayload> pdfData(@PathVariable Long counterpartyCompanyId) {
        ReconciliationPdfService.PdfPayload file = pdfService.generate(counterpartyCompanyId);
        return ApiResponse.ok(FileDataPayload.of(
                file.originalName(), MediaType.APPLICATION_PDF_VALUE, file.data()));
    }
}
