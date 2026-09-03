package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.config.FadadaProperties;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.entity.*;
import com.tradepass.integration.fadada.FadadaSigningGateway;
import com.tradepass.mapper.*;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FadadaSigningVersionTest {
    private final FadadaContractSignTaskMapper tasks = mock(FadadaContractSignTaskMapper.class);
    private final TradeContractMapper contracts = mock(TradeContractMapper.class);
    private final FadadaSigningGateway gateway = mock(FadadaSigningGateway.class);
    private final FadadaCompanyService companies = mock(FadadaCompanyService.class);
    private final TradeService trade = mock(TradeService.class);
    private final ContractArchiveService archive = mock(ContractArchiveService.class);
    private final ContractPdfService pdf = mock(ContractPdfService.class);
    private FadadaContractSigningService service;
    private TradeContract contract;
    private FadadaContractSignTask task;

    @BeforeEach void setUp() {
        MybatisTestSupport.initialize(FadadaContractSignTask.class);
        FadadaProperties properties = new FadadaProperties(); properties.setEnabled(true);
        properties.setAppId("app"); properties.setAppSecret("secret"); properties.setCallbackUrl("https://test/callback");
        service = new FadadaContractSigningService(tasks, contracts, mock(CompanyMapper.class),
                mock(AccessControlService.class), companies, gateway, pdf, archive, trade, properties, mock(JdbcTemplate.class));
        contract = new TradeContract(); contract.setId(12L); contract.setCompanyId(3L);
        contract.setCounterpartyCompanyId(4L); contract.setVersionNo(2); contract.setStatus("PENDING"); contract.setInitiatedBy(7L);
        task = new FadadaContractSignTask(); task.setId(20L); task.setContractId(12L); task.setVersionNo(2);
        task.setSignTaskId("v2"); task.setInitiatorCompanyId(3L); task.setCounterpartyCompanyId(4L);
        task.setInitiatorActorId("supplier"); task.setCounterpartyActorId("buyer");
        when(tasks.selectOne(any(Wrapper.class))).thenReturn(task);
        when(contracts.selectByIdForUpdate(12L)).thenReturn(contract);
        when(gateway.status("v2")).thenReturn(new FadadaSigningGateway.TaskStatus("v2", "task_finished", List.of()));
        FadadaCorpIdentity identity = new FadadaCorpIdentity(); identity.setOpenCorpId("corp3");
        when(companies.requireVerified(3L)).thenReturn(identity);
        when(pdf.fileName(any())).thenReturn("signed.pdf");
        when(gateway.downloadSignedPdf(anyString(), anyString(), anyString())).thenReturn("%PDF-test".getBytes());
        AuthContext.set(7L, 3L);
    }
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void lateOldVersionCallbackCannotActivateOrArchiveNewVersion() {
        task.setVersionNo(1); task.setSignTaskId("v1");
        service.syncBySignTaskId("v1");
        verifyNoInteractions(gateway, archive, trade);
    }

    @Test void callbackBeforeTaskCommitSignalsRetryInsteadOfDiscardingIt() {
        when(tasks.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.syncBySignTaskId("v2")).hasMessageContaining("尚未就绪");
        verifyNoInteractions(gateway, archive, trade);
    }

    @Test void signatureUsesFrozenMetadataInsteadOfLiveCompanyOrContractFields() throws Exception {
        ContractPayload frozen = snapshot(2);
        task.setContractSnapshot(new ObjectMapper().writeValueAsString(frozen));
        service.syncBySignTaskId("v2");
        verify(trade, never()).contractForElectronicSignature(any(), anyLong());
        verify(archive).archiveSignedPdf(eq(frozen), any(byte[].class), eq("v2"), eq(7L));
        verify(trade).activateAfterElectronicSignature(12L, 2, 7L);
        assertThat(task.getArchivedAt()).isNotNull();
    }

    @Test void wrongSnapshotVersionFailsWithoutArchiving() throws Exception {
        task.setContractSnapshot(new ObjectMapper().writeValueAsString(snapshot(1)));
        assertThatThrownBy(() -> service.syncBySignTaskId("v2")).hasMessageContaining("版本不一致");
        verifyNoInteractions(archive, trade);
    }

    @Test void duplicateCompletionDoesNotArchiveOrActivateTwice() {
        contract.setStatus("ACTIVE"); task.setArchivedAt(LocalDateTime.now());
        service.syncBySignTaskId("v2");
        verifyNoInteractions(archive, trade);
    }

    @Test void callbackCannotReactivateCancelledContract() {
        contract.setStatus("CANCELLED");
        service.syncBySignTaskId("v2");
        verifyNoInteractions(gateway, archive, trade);
    }

    @Test void manualSyncReturnsNewlyActivatedStatus() throws Exception {
        task.setContractSnapshot(new ObjectMapper().writeValueAsString(snapshot(2)));
        doAnswer(inv -> { contract.setStatus("ACTIVE"); return null; })
                .when(trade).activateAfterElectronicSignature(12L, 2, 7L);
        assertThat(service.syncCurrent(12L).canSign()).isFalse();
    }

    private ContractPayload snapshot(int version) {
        return new ContractPayload("12", "HT12", "3", "4", "买方", "SALE", "签署时合同", "模板",
                BigDecimal.TEN, null, null, "条款", "PENDING", version, "7", null, null, null,
                "签署时供方", "买方", "3", "4", "买方", "SALE", "INITIATOR");
    }
}
