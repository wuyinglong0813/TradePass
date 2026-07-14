package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SwitchCompanyRequest(@NotBlank(message = "企业 ID 不能为空") String companyId) {
}
