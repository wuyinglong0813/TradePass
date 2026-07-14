package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddCounterpartyRequest(@NotBlank String counterpartyName) {
}
