package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasc.open.api.utils.crypt.FddCryptUtil;
import com.tradepass.config.FadadaProperties;
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.mapper.FadadaCallbackEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class FadadaCallbackService {
    private static final Logger log = LoggerFactory.getLogger(FadadaCallbackService.class);
    private static final long MAX_CLOCK_SKEW_MILLIS = 300_000L;
    private final FadadaProperties properties;
    private final FadadaCallbackEventMapper eventMapper;
    private final FadadaCallbackProcessor processor;

    public FadadaCallbackService(FadadaProperties properties, FadadaCallbackEventMapper eventMapper,
                                 FadadaCallbackProcessor processor) {
        this.properties = properties;
        this.eventMapper = eventMapper;
        this.processor = processor;
    }

    public void accept(HttpHeaders headers, String bizContent) {
        String appId = headers.getFirst("X-FASC-App-Id");
        String signType = headers.getFirst("X-FASC-Sign-Type");
        String sign = headers.getFirst("X-FASC-Sign");
        String timestamp = headers.getFirst("X-FASC-Timestamp");
        String nonce = headers.getFirst("X-FASC-Nonce");
        String eventType = headers.getFirst("X-FASC-Event");
        if (!valid(appId, signType, sign, timestamp, nonce, eventType, bizContent)) {
            log.warn("Ignored invalid electronic signature callback: event={}", safe(eventType));
            return;
        }
        String eventId = safe(eventType) + ":" + safe(nonce);
        FadadaCallbackEvent event = eventMapper.selectOne(new LambdaQueryWrapper<FadadaCallbackEvent>()
                .eq(FadadaCallbackEvent::getEventId, eventId).last("LIMIT 1"));
        if (event != null) return;
        event = new FadadaCallbackEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setSubjectType("CALLBACK");
        event.setPayloadSha256(sha256(bizContent));
        event.setStatus("RECEIVED");
        event.setReceivedAt(LocalDateTime.now());
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        processor.processAsync(event.getId(), eventType, bizContent);
    }

    private boolean valid(String appId, String signType, String sign, String timestamp,
                          String nonce, String event, String bizContent) {
        if (!properties.isEnabled() || !hasText(properties.getAppId()) || !hasText(properties.getAppSecret())) return false;
        if (!properties.getAppId().equals(appId) || !"HMAC-SHA256".equals(signType)
                || !hasText(sign) || !hasText(nonce) || nonce.length() > 32 || !hasText(event)
                || bizContent == null) return false;
        try {
            long eventTime = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().toEpochMilli() - eventTime) > MAX_CLOCK_SKEW_MILLIS) return false;
            Map<String, String> parameters = new HashMap<>();
            parameters.put("X-FASC-App-Id", appId);
            parameters.put("X-FASC-Sign-Type", signType);
            parameters.put("X-FASC-Timestamp", timestamp);
            parameters.put("X-FASC-Nonce", nonce);
            parameters.put("X-FASC-Event", event);
            parameters.put("bizContent", bizContent);
            String expected = FddCryptUtil.sign(FddCryptUtil.sortParameters(parameters), timestamp,
                    properties.getAppSecret());
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    sign.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return false;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Callback digest failed", exception);
        }
    }
    private String safe(String value) { return value == null ? "" : value; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
