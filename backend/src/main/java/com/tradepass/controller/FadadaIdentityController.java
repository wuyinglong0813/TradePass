package com.tradepass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradepass.common.ApiResponse;
import com.tradepass.dto.response.FadadaAuthUrlPayload;
import com.tradepass.dto.response.FadadaCompanyIdentityPayload;
import com.tradepass.dto.response.PersonalIdentityPayload;
import com.tradepass.dto.response.ServiceUrlPayload;
import com.tradepass.service.FadadaCallbackService;
import com.tradepass.service.FadadaCompanyService;
import com.tradepass.service.FadadaPersonalIdentityService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fadada")
public class FadadaIdentityController {
    private final FadadaPersonalIdentityService personalIdentityService;
    private final FadadaCompanyService companyService;
    private final FadadaCallbackService callbackService;

    public FadadaIdentityController(FadadaPersonalIdentityService personalIdentityService,
                                    FadadaCompanyService companyService,
                                    FadadaCallbackService callbackService) {
        this.personalIdentityService = personalIdentityService;
        this.companyService = companyService;
        this.callbackService = callbackService;
    }

    @GetMapping("/users/me/identity")
    public ApiResponse<PersonalIdentityPayload> currentIdentity() {
        return ApiResponse.ok(personalIdentityService.current());
    }

    @PostMapping("/users/me/identity/sync")
    public ApiResponse<PersonalIdentityPayload> syncIdentity() {
        return ApiResponse.ok(personalIdentityService.syncCurrent());
    }

    @PostMapping("/users/me/auth-url")
    public ApiResponse<FadadaAuthUrlPayload> createAuthUrl() {
        return ApiResponse.ok(personalIdentityService.createAuthUrl());
    }

    @GetMapping("/companies/{companyId}/identity")
    public ApiResponse<FadadaCompanyIdentityPayload> companyIdentity(@PathVariable long companyId) {
        return ApiResponse.ok(companyService.current(companyId));
    }

    @PostMapping("/companies/{companyId}/identity/sync")
    public ApiResponse<FadadaCompanyIdentityPayload> syncCompanyIdentity(@PathVariable long companyId) {
        return ApiResponse.ok(companyService.syncCurrent(companyId));
    }

    @PostMapping("/companies/{companyId}/auth-url")
    public ApiResponse<ServiceUrlPayload> companyAuthUrl(@PathVariable long companyId) {
        return ApiResponse.ok(companyService.createAuthUrl(companyId));
    }

    @PostMapping("/companies/{companyId}/seal-manage-url")
    public ApiResponse<ServiceUrlPayload> sealManageUrl(@PathVariable long companyId) {
        return ApiResponse.ok(companyService.createSealManageUrl(companyId));
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> callback(@RequestHeader HttpHeaders headers,
                                           @RequestParam("bizContent") String bizContent) {
        callbackService.accept(headers, bizContent);
        return ResponseEntity.ok("{\"msg\":\"success\"}");
    }
}
