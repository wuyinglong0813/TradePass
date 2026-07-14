package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateContractRequest(@NotBlank String counterpartyName, @NotBlank String name, String templateName, @NotNull BigDecimal amount, String startDate, String endDate, String terms) {
}
