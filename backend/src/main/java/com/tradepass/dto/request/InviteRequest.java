package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InviteRequest(@NotBlank String companyId) {
}
