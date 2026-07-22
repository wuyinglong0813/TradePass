package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.LogisticsDocument;
import com.tradepass.service.LogisticsDocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LogisticsDocumentController {
    private final LogisticsDocumentService logisticsDocumentService;

    public LogisticsDocumentController(LogisticsDocumentService logisticsDocumentService) {
        this.logisticsDocumentService = logisticsDocumentService;
    }

    @GetMapping("/contracts/{contractId}/logistics-documents")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long contractId) {
        return ApiResponse.ok(logisticsDocumentService.listDocuments(contractId));
    }

    @PostMapping(value = "/contracts/{contractId}/logistics-documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@PathVariable Long contractId,
                                                   @RequestParam(value = "originalName", required = false)
                                                   String originalName,
                                                   @RequestParam("file") MultipartFile file) {
        try {
            return ApiResponse.ok(logisticsDocumentService.upload(
                    contractId,
                    originalName == null || originalName.isBlank()
                            ? file.getOriginalFilename() : originalName,
                    file.getBytes()));
        } catch (IOException exception) {
            throw new BusinessException("物流单图片读取失败，请重新选择");
        }
    }

    @GetMapping("/logistics-documents/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        LogisticsDocument document = logisticsDocumentService.getImage(id);
        byte[] imageData = document.getImageData();
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(imageData.length)
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .body(imageData);
    }
}
