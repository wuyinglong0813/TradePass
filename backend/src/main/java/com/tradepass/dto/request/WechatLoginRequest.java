package com.tradepass.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(
        @NotBlank(message = "微信登录 code 不能为空") String code,
        String nickName,
        String avatarUrl,
        String phone,
        String phoneCode
) {
}
