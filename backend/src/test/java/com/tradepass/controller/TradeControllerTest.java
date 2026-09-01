package com.tradepass.controller;

import com.tradepass.dto.response.FileChunkDataPayload;
import com.tradepass.service.FadadaContractSigningService;
import com.tradepass.service.TradeService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeControllerTest {

    @Test
    void returnsSignedArchivePreviewInBoundedChunks() {
        FadadaContractSigningService signingService = mock(FadadaContractSigningService.class);
        TradeController controller = new TradeController(mock(TradeService.class));
        controller.setSigningService(signingService);
        when(signingService.signedPreview(12L)).thenReturn(
                new FadadaContractSigningService.SignedPreview(
                        "合同签章页.png", "preview".getBytes(StandardCharsets.UTF_8)));

        FileChunkDataPayload chunk = controller.signedContractPreviewChunk(12L, 3, 3).data();

        assertThat(new String(Base64.getDecoder().decode(chunk.contentBase64()), StandardCharsets.UTF_8))
                .isEqualTo("vie");
        assertThat(chunk.offset()).isEqualTo(3);
        assertThat(chunk.length()).isEqualTo(3);
        assertThat(chunk.totalSize()).isEqualTo(7);
        assertThat(chunk.eof()).isFalse();
    }
}
