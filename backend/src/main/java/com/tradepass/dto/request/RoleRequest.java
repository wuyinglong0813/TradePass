package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoleRequest(@NotBlank String companyId, @NotBlank String name, @NotNull List<String> permissions) {
}
