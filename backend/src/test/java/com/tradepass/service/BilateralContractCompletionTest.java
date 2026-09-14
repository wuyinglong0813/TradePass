package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.TradeContractMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BilateralContractCompletionTest {
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void pendingInboundBlocksEndRequestBeforeAnyApprovalRecordIsCreated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TradeContractMapper contracts = mock(TradeContractMapper.class);
        TradeContract contract = new TradeContract();
        contract.setId(12L); contract.setCompanyId(3L); contract.setCounterpartyCompanyId(4L); contract.setStatus("ACTIVE");
        when(contracts.selectByIdForUpdate(12L)).thenReturn(contract);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(12L))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("FROM business_document") && sql.contains("'ACKNOWLEDGED'") ? 1L : 0L;
        });
        var service = new BilateralActionService(jdbc, contracts, mock(AccessControlService.class),
                mock(AuditLogService.class), mock(ReconciliationAccountService.class),
                mock(SalesOrderInventoryService.class), mock(ApprovalService.class));
        AuthContext.set(7L, 3L);
        assertThatThrownBy(() -> service.request("CONTRACT", 12L, "END", "结束合同", true))
                .hasMessageContaining("待入库");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
