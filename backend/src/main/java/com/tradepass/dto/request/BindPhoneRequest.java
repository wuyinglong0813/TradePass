package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BindPhoneRequest(@NotBlank(message = "手机号不能为空") String phone) {
}
