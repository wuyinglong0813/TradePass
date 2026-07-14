package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerificationRequest(@NotBlank String companyId) {
}
