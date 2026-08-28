package com.tradepass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradepass.common.ApiResponse;
import com.tradepass.dto.response.FadadaAuthUrlPayload;
import com.tradepass.dto.response.PersonalIdentityPayload;
import com.tradepass.service.FadadaPersonalIdentityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fadada")
public class FadadaIdentityController {
    private final FadadaPersonalIdentityService personalIdentityService;

    public FadadaIdentityController(FadadaPersonalIdentityService personalIdentityService) {
        this.personalIdentityService = personalIdentityService;
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

    @PostMapping("/callback")
    public ApiResponse<Map<String, String>> callback(@RequestParam(value = "token", required = false) String token,
                                                     @RequestBody JsonNode payload) {
        personalIdentityService.handleCallback(token, payload);
        return ApiResponse.ok(Map.of("result", "success"));
    }
}
