package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.FadadaContractSignTask;
import com.tradepass.entity.TradeContract;
import com.tradepass.integration.fadada.FadadaSigningGateway;
import com.tradepass.mapper.FadadaContractSignTaskMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractSigningCancellationServiceTest {
    private final FadadaContractSignTaskMapper mapper = mock(FadadaContractSignTaskMapper.class);
    private final FadadaSigningGateway gateway = mock(FadadaSigningGateway.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final ContractSigningCancellationService service = new ContractSigningCancellationService(mapper, gateway, access);
    private TradeContract contract;
    private FadadaContractSignTask task;

    @BeforeEach void setUp() {
        MybatisTestSupport.initialize(FadadaContractSignTask.class);
        AuthContext.set(7L, 3L);
        contract = new TradeContract(); contract.setId(12L); contract.setCompanyId(3L);
        contract.setCounterpartyCompanyId(4L); contract.setVersionNo(2);
        task = new FadadaContractSignTask(); task.setId(20L); task.setSignTaskId("v2-task");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(task);
    }
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void foreignCompanyCannotContactProvider() {
        AuthContext.set(99L, 99L);
        assertThatThrownBy(() -> service.cancelForChange(contract, "撤回")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(gateway, mapper);
    }

    @Test void insufficientPermissionCannotContactProvider() {
        doThrow(new BusinessException("无签署权限")).when(access).requirePermission(3L, "contract_sign");
        assertThatThrownBy(() -> service.cancelForChange(contract, "撤回")).hasMessage("无签署权限");
        verifyNoInteractions(gateway, mapper);
    }

    @Test void changeWaitsForConfirmedProviderCancellation() {
        when(gateway.status("v2-task")).thenReturn(status("sign_progress"), status("task_terminated"));
        service.cancelForChange(contract, "修改");
        var order = inOrder(gateway, mapper);
        order.verify(mapper).selectOne(any(Wrapper.class));
        order.verify(gateway).status("v2-task"); order.verify(gateway).cancel("v2-task", "修改");
        order.verify(gateway).status("v2-task"); order.verify(mapper).updateById(task);
        assertThat(task.getProviderStatus()).isEqualTo("task_terminated");
    }

    @Test void retryAfterRemoteSuccessDoesNotCancelAgain() {
        when(gateway.status("v2-task")).thenReturn(status("task_terminated"));
        service.cancelForChange(contract, "修改");
        verify(gateway, never()).cancel(anyString(), anyString());
        verify(mapper).updateById(task);
    }

    @Test void completedSignatureCannotBeReplacedByNewTerms() {
        when(gateway.status("v2-task")).thenReturn(status("task_finished"));
        assertThatThrownBy(() -> service.cancelForChange(contract, "修改")).hasMessageContaining("已完成签署");
        verify(gateway, never()).cancel(anyString(), anyString()); verify(mapper, never()).updateById(any(FadadaContractSignTask.class));
    }

    @Test void unconfirmedCancellationDoesNotPermitLocalChange() {
        when(gateway.status("v2-task")).thenReturn(status("sign_progress"));
        assertThatThrownBy(() -> service.cancelForChange(contract, "修改")).hasMessageContaining("尚未确认");
        verify(mapper, never()).updateById(any(FadadaContractSignTask.class));
    }

    private FadadaSigningGateway.TaskStatus status(String value) {
        return new FadadaSigningGateway.TaskStatus("v2-task", value, List.of());
    }
}
