package com.tradepass.controller;

import com.tradepass.common.ApiResponse;
import com.tradepass.dto.request.CertificationReviewRequest;
import com.tradepass.dto.response.CertificationApplicationPayload;
import com.tradepass.service.CompanyCertificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CompanyCertificationController {
    private final CompanyCertificationService certificationService;

    public CompanyCertificationController(CompanyCertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @GetMapping("/me/company-certification-applications")
    public ApiResponse<List<CertificationApplicationPayload>> myApplications() {
        return ApiResponse.ok(certificationService.myApplications());
    }

    @PostMapping("/company-certifications/provider-callback")
    public ApiResponse<CertificationApplicationPayload> providerCallback(
            @RequestHeader(value = "X-Verification-Token", required = false) String token,
            @Valid @RequestBody CertificationReviewRequest request) {
        return ApiResponse.ok(certificationService.review(token, request));
    }
}
