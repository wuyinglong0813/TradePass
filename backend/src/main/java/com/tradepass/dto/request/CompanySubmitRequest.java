package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompanySubmitRequest(String id, @NotBlank String name, @NotBlank String creditCode, @NotBlank String legalPersonName) {
}
