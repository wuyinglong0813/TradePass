package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SealRequest(@NotBlank String companyId, @NotBlank String fileUrl, @NotBlank String usage) {
}
