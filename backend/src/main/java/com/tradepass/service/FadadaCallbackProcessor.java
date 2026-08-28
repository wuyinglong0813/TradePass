package com.tradepass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.mapper.FadadaCallbackEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class FadadaCallbackProcessor {
    private static final Logger log = LoggerFactory.getLogger(FadadaCallbackProcessor.class);
    private final FadadaCallbackEventMapper eventMapper;
    private final FadadaPersonalIdentityService personalService;
    private final FadadaCompanyService companyService;
    private final FadadaContractSigningService signingService;
    private final ObjectMapper objectMapper;

    public FadadaCallbackProcessor(FadadaCallbackEventMapper eventMapper,
                                   FadadaPersonalIdentityService personalService,
                                   FadadaCompanyService companyService,
                                   FadadaContractSigningService signingService,
                                   ObjectMapper objectMapper) {
        this.eventMapper = eventMapper;
        this.personalService = personalService;
        this.companyService = companyService;
        this.signingService = signingService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void processAsync(Long eventId, String eventType, String bizContent) {
        FadadaCallbackEvent event = eventMapper.selectById(eventId);
        if (event == null || !"RECEIVED".equals(event.getStatus())) return;
        try {
            JsonNode data = objectMapper.readTree(bizContent);
            String clientUserId = text(data, "clientUserId");
            String clientCorpId = text(data, "clientCorpId");
            String openCorpId = text(data, "openCorpId");
            String signTaskId = text(data, "signTaskId");
            // Corporate authorization callbacks also carry the operator's clientUserId.
            // Prefer the most specific business identifier so they are not mistaken for
            // personal-identification callbacks.
            if (hasText(signTaskId)) {
                signingService.syncBySignTaskId(signTaskId);
                event.setSubjectType("CONTRACT");
                event.setStatus("PROCESSED");
            } else if (hasText(clientCorpId)) {
                var payload = companyService.syncCallback(clientCorpId, openCorpId, data);
                event.setSubjectType("COMPANY");
                if (payload != null) event.setSubjectId(Long.valueOf(payload.companyId()));
                event.setStatus(payload == null ? "IGNORED" : "PROCESSED");
            } else if (hasText(openCorpId)) {
                var payload = companyService.syncCallback(null, openCorpId, data);
                event.setSubjectType("COMPANY");
                if (payload != null) event.setSubjectId(Long.valueOf(payload.companyId()));
                event.setStatus(payload == null ? "IGNORED" : "PROCESSED");
            } else if (hasText(clientUserId)) {
                var payload = personalService.syncCallback(clientUserId, data);
                event.setSubjectType("USER");
                if (payload != null) event.setStatus("PROCESSED"); else event.setStatus("IGNORED");
            } else {
                event.setStatus("IGNORED");
            }
        } catch (Exception exception) {
            log.warn("Electronic signature callback processing failed: event={}", eventType, exception);
            event.setStatus("FAILED");
            event.setFailureReason(shortMessage(exception));
        }
        event.setProcessedAt(LocalDateTime.now());
        eventMapper.updateById(event);
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String shortMessage(Exception exception) {
        String value = exception.getMessage();
        if (!hasText(value)) return "回调处理失败";
        return value.substring(0, Math.min(value.length(), 512));
    }
}
