package com.tradepass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.mapper.FadadaCallbackEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FadadaCallbackProcessorTest {
    private final FadadaCallbackEventMapper events = mock(FadadaCallbackEventMapper.class);
    private final FadadaContractSigningService signing = mock(FadadaContractSigningService.class);
    private final FadadaCompanyService companies = mock(FadadaCompanyService.class);
    private final FadadaPersonalIdentityService people = mock(FadadaPersonalIdentityService.class);
    private final FadadaCallbackProcessor processor = new FadadaCallbackProcessor(events, people, companies, signing, new ObjectMapper());
    private FadadaCallbackEvent event;

    @BeforeEach void record() {
        event = new FadadaCallbackEvent(); event.setId(12L); event.setStatus("RECEIVED");
        event.setAttemptCount(1); event.setRetryPayload("{\"signTaskId\":\"task-1\"}");
        when(events.claim(eq(12L), anyString(), any(), any())).thenAnswer(inv -> {
            event.setProcessingToken(inv.getArgument(1)); return 1;
        });
        when(events.selectById(12L)).thenReturn(event);
        when(events.finish(eq(event), anyString())).thenReturn(1);
    }

    @Test void failureSchedulesDurableRetryAndRetryCompletes() {
        doThrow(new IllegalStateException("provider temporarily unavailable")).doNothing()
                .when(signing).syncBySignTaskId("task-1");
        processor.process(12L);
        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getNextAttemptAt()).isBetween(LocalDateTime.now().plusSeconds(20), LocalDateTime.now().plusSeconds(40));
        assertThat(event.getFailureReason()).contains("temporarily");
        processor.process(12L);
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        assertThat(event.getNextAttemptAt()).isNull();
        assertThat(event.getFailureReason()).isNull();
        verify(events, times(2)).finish(eq(event), anyString());
    }

    @Test void workerThatCannotClaimDoesNotExecuteBusinessOperation() {
        when(events.claim(eq(12L), anyString(), any(), any())).thenReturn(0);
        processor.process(12L);
        verifyNoInteractions(signing, companies, people);
        verify(events, never()).finish(any(), anyString());
    }

    @Test void companyCallbackBeforeIdentityCommitIsRetriedAndNotMisroutedToUser() {
        event.setRetryPayload("{\"clientCorpId\":\"corp-1\",\"clientUserId\":\"user-1\"}");
        processor.process(12L);
        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getNextAttemptAt()).isNotNull();
        verify(companies).syncCallback(eq("corp-1"), isNull(), any());
        verifyNoInteractions(people, signing);
    }

    @Test void unsupportedCallbackIsAcknowledgedWithoutRetry() {
        event.setRetryPayload("{}");
        processor.process(12L);
        assertThat(event.getStatus()).isEqualTo("IGNORED");
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test void repeatedFailuresUseCappedBackoff() {
        event.setAttemptCount(100);
        doThrow(new IllegalStateException("unavailable")).when(signing).syncBySignTaskId(anyString());
        processor.process(12L);
        assertThat(event.getNextAttemptAt()).isBetween(LocalDateTime.now().plusMinutes(59), LocalDateTime.now().plusMinutes(61));
    }
}
