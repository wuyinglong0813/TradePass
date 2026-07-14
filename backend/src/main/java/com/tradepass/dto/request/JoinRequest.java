package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JoinRequest(@NotBlank String code) {
}
