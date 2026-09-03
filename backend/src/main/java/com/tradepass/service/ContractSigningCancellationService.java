package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.FadadaContractSignTask;
import com.tradepass.entity.TradeContract;
import com.tradepass.integration.fadada.FadadaSigningGateway;
import com.tradepass.mapper.FadadaContractSignTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Called after the business operation has authorized and locked the contract. */
@Service
public class ContractSigningCancellationService {
    private final FadadaContractSignTaskMapper taskMapper;
    private final FadadaSigningGateway gateway;
    private final AccessControlService accessControl;

    public ContractSigningCancellationService(FadadaContractSignTaskMapper taskMapper,
                                               FadadaSigningGateway gateway,
                                               AccessControlService accessControl) {
        this.taskMapper = taskMapper;
        this.gateway = gateway;
        this.accessControl = accessControl;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForChange(TradeContract contract, String reason) {
        long companyId = AuthContext.requireCompanyId();
        accessControl.requirePermission(companyId, "contract_sign");
        if (!Long.valueOf(companyId).equals(contract.getCompanyId())
                && !Long.valueOf(companyId).equals(contract.getCounterpartyCompanyId())) {
            throw new BusinessException("合同不存在");
        }
        FadadaContractSignTask task = taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .eq(FadadaContractSignTask::getContractId, contract.getId())
                .eq(FadadaContractSignTask::getVersionNo, contract.getVersionNo() == null ? 1 : contract.getVersionNo())
                .last("LIMIT 1 FOR UPDATE"));
        if (task == null || task.getSignTaskId() == null || task.getSignTaskId().isBlank()) return;
        if (task.getArchivedAt() != null || task.getAbolishedSignTaskId() != null) {
            throw new BusinessException("合同已完成签署，请同步状态后处理");
        }
        // Always consult the provider: a previous cancellation can have succeeded remotely
        // even when our transaction rolled back or the HTTP response was lost.
        String status = status(task.getSignTaskId());
        if ("task_finished".equalsIgnoreCase(status) || "sign_completed".equalsIgnoreCase(status)) {
            throw new BusinessException("合同已完成签署，请同步状态后处理");
        }
        if (!stopped(status)) {
            gateway.cancel(task.getSignTaskId(), reason);
            status = status(task.getSignTaskId());
            if (!stopped(status)) throw new BusinessException("签署撤销结果尚未确认，请稍后重试");
        }
        task.setProviderStatus(status);
        task.setLastError(reason);
        taskMapper.updateById(task);
    }

    private String status(String taskId) {
        var result = gateway.status(taskId);
        if (result == null || result.status() == null) {
            throw new BusinessException("无法确认签署状态，请稍后重试");
        }
        return result.status();
    }

    private boolean stopped(String status) {
        return "task_terminated".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)
                || "task_expired".equalsIgnoreCase(status);
    }
}
