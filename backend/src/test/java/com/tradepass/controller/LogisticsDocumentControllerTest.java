package com.tradepass.controller;

import com.tradepass.dto.response.FileChunkDataPayload;
import com.tradepass.entity.LogisticsDocument;
import com.tradepass.service.LogisticsDocumentService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogisticsDocumentControllerTest {

    @Test
    void returnsLogisticsImagesInBoundedChunks() {
        LogisticsDocumentService service = mock(LogisticsDocumentService.class);
        LogisticsDocumentController controller = new LogisticsDocumentController(service);
        LogisticsDocument document = new LogisticsDocument();
        document.setId(9L);
        document.setOriginalName("物流单.png");
        document.setContentType("image/png");
        document.setImageData("logistics".getBytes(StandardCharsets.UTF_8));
        when(service.getImage(9L)).thenReturn(document);

        FileChunkDataPayload chunk = controller.imageChunkData(9L, 3, 4).data();

        assertThat(new String(Base64.getDecoder().decode(chunk.contentBase64()), StandardCharsets.UTF_8))
                .isEqualTo("isti");
        assertThat(chunk.offset()).isEqualTo(3);
        assertThat(chunk.length()).isEqualTo(4);
        assertThat(chunk.totalSize()).isEqualTo(9);
        assertThat(chunk.eof()).isFalse();
    }
}
