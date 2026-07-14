package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ApproveRequest(@NotBlank String roleCode, List<String> customPermissions) {
}
