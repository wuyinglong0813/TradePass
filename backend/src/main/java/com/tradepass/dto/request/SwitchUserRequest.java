package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SwitchUserRequest(@NotBlank(message = "用户 ID 不能为空") String userId) {
}
