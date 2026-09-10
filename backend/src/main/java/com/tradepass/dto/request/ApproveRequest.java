package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ApproveRequest(String roleCode, List<String> customPermissions, List<@NotBlank String> roleCodes) {
    public ApproveRequest(String roleCode, List<String> customPermissions) {
        this(roleCode, customPermissions, null);
    }
}
