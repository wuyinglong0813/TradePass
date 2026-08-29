package com.tradepass.integration.fadada;

import com.fasc.open.api.v5_1.res.signtask.SignTaskActorGetUrlRes;
import com.tradepass.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SdkFadadaSigningGatewayTest {

    @Test
    void usesMiniProgramEmbedUrlInsteadOfDistributedShortUrl() {
        SignTaskActorGetUrlRes response = new SignTaskActorGetUrlRes();
        response.setActorSignTaskUrl("https://test.fdd9.cn/short-link");
        response.setActorSignTaskEmbedUrl("https://80005620.uat-e.fadada.com/connect?ticket=one-time");

        assertThat(SdkFadadaSigningGateway.requiredActorEmbedUrl(response))
                .isEqualTo("https://80005620.uat-e.fadada.com/connect?ticket=one-time");
    }

    @Test
    void doesNotFallBackToShortUrlWhenEmbedUrlIsMissing() {
        SignTaskActorGetUrlRes response = new SignTaskActorGetUrlRes();
        response.setActorSignTaskUrl("https://test.fdd9.cn/short-link");

        assertThatThrownBy(() -> SdkFadadaSigningGateway.requiredActorEmbedUrl(response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未获取到小程序合同签署地址，请稍后重试");
    }
}
