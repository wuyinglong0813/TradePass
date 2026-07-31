package com.tradepass.dto.request;

import jakarta.validation.constraints.Size;

public record WechatLoginRequest(
        @Size(max = 128, message = "微信登录 code 格式不正确") String code,
        @Size(max = 128, message = "昵称过长") String nickName,
        String avatarUrl,
        @Size(max = 32, message = "手机号格式不正确") String phone,
        @Size(max = 256, message = "手机号凭证格式不正确") String phoneCode
) {
}
