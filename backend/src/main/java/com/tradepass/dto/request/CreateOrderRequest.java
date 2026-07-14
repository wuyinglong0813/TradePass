package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateOrderRequest(@NotBlank String direction, @NotBlank String counterpartyName, @NotNull BigDecimal amount, @NotNull LocalDate orderDate) {
}
