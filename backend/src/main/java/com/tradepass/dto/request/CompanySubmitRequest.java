package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanySubmitRequest(
        String id,
        @NotBlank String name,
        @NotBlank String creditCode,
        @NotBlank String legalPersonName,
        @Size(max = 256) String registeredAddress,
        @Size(max = 32) String contactPhone,
        @Size(max = 128) String bankName,
        @Size(max = 64) String bankAccount
) {
    public CompanySubmitRequest(String id, String name, String creditCode, String legalPersonName) {
        this(id, name, creditCode, legalPersonName, null, null, null, null);
    }
}
