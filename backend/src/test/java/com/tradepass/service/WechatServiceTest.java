package com.tradepass.service;

import com.tradepass.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WechatServiceTest {

    @Test
    void acceptsDevelopmentCredentialOnlyInDevelopment() {
        assertThat(new WechatService("app", "", true).resolveOpenid("dev-user-1"))
                .isEqualTo("dev-user-1");

        assertThatThrownBy(() -> new WechatService("app", "", false).resolveOpenid("dev-user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开发登录凭证在当前环境不可用");
    }

    @Test
    void rejectsWechatCallsWhenSecretIsMissing() {
        WechatService service = new WechatService("app", "", false);

        assertThatThrownBy(() -> service.resolveOpenid("real-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未配置 WECHAT_APP_SECRET，无法完成微信登录");
        assertThatThrownBy(() -> service.resolvePhoneByCode("phone-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未配置 WECHAT_APP_SECRET，无法获取 access_token");
    }
}
