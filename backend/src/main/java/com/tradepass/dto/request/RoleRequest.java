package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RoleRequest(@NotBlank String companyId, @NotBlank String name, @NotEmpty List<String> permissions) {
}
