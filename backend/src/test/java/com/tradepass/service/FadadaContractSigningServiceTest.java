package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.config.FadadaProperties;
import com.tradepass.entity.FadadaContractSignTask;
import com.tradepass.entity.FadadaCorpIdentity;
import com.tradepass.entity.TradeContract;
import com.tradepass.integration.fadada.FadadaSigningGateway;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.FadadaContractSignTaskMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FadadaContractSigningServiceTest {

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void cachesImmutableSignedPreviewInsteadOfDownloadingProviderZipForEveryChunk() {
        MybatisTestSupport.initialize(FadadaContractSignTask.class, TradeContract.class);
        FadadaContractSignTaskMapper taskMapper = mock(FadadaContractSignTaskMapper.class);
        TradeContractMapper contractMapper = mock(TradeContractMapper.class);
        FadadaCompanyService companyService = mock(FadadaCompanyService.class);
        FadadaSigningGateway gateway = mock(FadadaSigningGateway.class);
        FadadaProperties properties = configuredProperties();
        FadadaContractSigningService service = new FadadaContractSigningService(
                taskMapper, contractMapper, mock(CompanyMapper.class),
                mock(AccessControlService.class), companyService, gateway,
                mock(ContractPdfService.class), mock(ContractArchiveService.class),
                mock(TradeService.class), properties, mock(JdbcTemplate.class));

        TradeContract contract = new TradeContract();
        contract.setId(12L);
        contract.setCompanyId(3L);
        contract.setCounterpartyCompanyId(4L);
        contract.setVersionNo(1);
        FadadaContractSignTask task = new FadadaContractSignTask();
        task.setContractId(12L);
        task.setVersionNo(1);
        task.setSignTaskId("sign-task-12");
        task.setArchivedAt(LocalDateTime.now());
        FadadaCorpIdentity identity = new FadadaCorpIdentity();
        identity.setOpenCorpId("corp-3");
        byte[] preview = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        when(contractMapper.selectById(12L)).thenReturn(contract);
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(companyService.requireVerified(3L)).thenReturn(identity);
        when(gateway.downloadSignedPreviewPage("sign-task-12", "corp-3")).thenReturn(preview);
        AuthContext.set(7L, 3L);

        assertThat(service.signedPreview(12L).data()).containsExactly(preview);
        assertThat(service.signedPreview(12L).data()).containsExactly(preview);
        verify(gateway, times(1)).downloadSignedPreviewPage("sign-task-12", "corp-3");
    }

    private FadadaProperties configuredProperties() {
        FadadaProperties properties = new FadadaProperties();
        properties.setEnabled(true);
        properties.setAppId("app-id");
        properties.setAppSecret("app-secret");
        properties.setServerUrl("https://api.fadada.com/api/v5");
        properties.setCallbackUrl("https://example.test/callback");
        return properties;
    }
}
