package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasc.open.api.utils.crypt.FddCryptUtil;
import com.tradepass.config.FadadaProperties;
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.mapper.FadadaCallbackEventMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FadadaCallbackServiceTest {
    private FadadaCallbackEventMapper eventMapper;
    private FadadaCallbackProcessor processor;
    private FadadaCallbackService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(FadadaCallbackEvent.class);
        eventMapper = mock(FadadaCallbackEventMapper.class);
        processor = mock(FadadaCallbackProcessor.class);
        FadadaProperties properties = new FadadaProperties();
        properties.setEnabled(true);
        properties.setAppId("test-app-id");
        properties.setAppSecret("test-secret");
        service = new FadadaCallbackService(properties, eventMapper, processor);
    }

    @Test
    void acceptsOfficialHmacCallbackOnce() throws Exception {
        String body = "{\"signTaskId\":\"task-8\",\"signTaskStatus\":\"task_finished\"}";
        AtomicReference<FadadaCallbackEvent> saved = new AtomicReference<>();
        when(eventMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> saved.get());
        doAnswer(invocation -> {
            FadadaCallbackEvent event = invocation.getArgument(0);
            event.setId(28L);
            saved.set(event);
            return 1;
        }).when(eventMapper).insert(any(FadadaCallbackEvent.class));
        HttpHeaders headers = signedHeaders(body, "sign-task-finished", "nonce-8");

        service.accept(headers, body);
        service.accept(headers, body);

        assertThat(saved.get().getEventId()).isEqualTo("sign-task-finished:nonce-8");
        assertThat(saved.get().getPayloadSha256()).hasSize(64);
        verify(processor).processAsync(28L, "sign-task-finished", body);
    }

    @Test
    void acknowledgesButIgnoresInvalidSignature() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-FASC-App-Id", "test-app-id");
        headers.set("X-FASC-Sign-Type", "HMAC-SHA256");
        headers.set("X-FASC-Sign", "invalid");
        headers.set("X-FASC-Timestamp", String.valueOf(Instant.now().toEpochMilli()));
        headers.set("X-FASC-Nonce", "nonce-9");
        headers.set("X-FASC-Event", "sign-task-finished");

        service.accept(headers, "{}");

        verify(eventMapper, never()).insert(any(FadadaCallbackEvent.class));
        verify(processor, never()).processAsync(any(), any(), any());
    }

    private HttpHeaders signedHeaders(String body, String event, String nonce) throws Exception {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        Map<String, String> parameters = new HashMap<>();
        parameters.put("X-FASC-App-Id", "test-app-id");
        parameters.put("X-FASC-Sign-Type", "HMAC-SHA256");
        parameters.put("X-FASC-Timestamp", timestamp);
        parameters.put("X-FASC-Nonce", nonce);
        parameters.put("X-FASC-Event", event);
        parameters.put("bizContent", body);
        String signature = FddCryptUtil.sign(FddCryptUtil.sortParameters(parameters), timestamp, "test-secret");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-FASC-App-Id", "test-app-id");
        headers.set("X-FASC-Sign-Type", "HMAC-SHA256");
        headers.set("X-FASC-Sign", signature);
        headers.set("X-FASC-Timestamp", timestamp);
        headers.set("X-FASC-Nonce", nonce);
        headers.set("X-FASC-Event", event);
        return headers;
    }
}
