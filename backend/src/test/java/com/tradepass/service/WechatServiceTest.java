package com.tradepass.service;

import com.tradepass.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void reusesSharedAccessTokenFromRedis() {
        RedisCacheService redisCache = mock(RedisCacheService.class);
        long expiresAt = Instant.now().plusSeconds(600).getEpochSecond();
        when(redisCache.get("wechat:access-token:app"))
                .thenReturn(expiresAt + "\nshared-token");
        WechatService service = new WechatService(
                "app", "secret", false, redisCache, Duration.ofMinutes(5));

        String token = ReflectionTestUtils.invokeMethod(service, "getAccessToken");

        assertThat(token).isEqualTo("shared-token");
    }
}
