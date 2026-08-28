package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.config.FadadaProperties;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.dto.response.ContractSigningPayload;
import com.tradepass.dto.response.ServiceUrlPayload;
import com.tradepass.entity.Company;
import com.tradepass.entity.FadadaContractSignTask;
import com.tradepass.entity.FadadaCorpIdentity;
import com.tradepass.entity.TradeContract;
import com.tradepass.integration.fadada.FadadaSigningGateway;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.FadadaContractSignTaskMapper;
import com.tradepass.mapper.TradeContractMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FadadaContractSigningService {
    private final FadadaContractSignTaskMapper taskMapper;
    private final TradeContractMapper contractMapper;
    private final CompanyMapper companyMapper;
    private final AccessControlService accessControl;
    private final FadadaCompanyService companyService;
    private final FadadaSigningGateway gateway;
    private final ContractPdfService pdfService;
    private final ContractArchiveService archiveService;
    private final TradeService tradeService;
    private final FadadaProperties properties;
    private final JdbcTemplate jdbc;

    public FadadaContractSigningService(FadadaContractSignTaskMapper taskMapper,
                                        TradeContractMapper contractMapper, CompanyMapper companyMapper,
                                        AccessControlService accessControl, FadadaCompanyService companyService,
                                        FadadaSigningGateway gateway, ContractPdfService pdfService,
                                        ContractArchiveService archiveService, TradeService tradeService,
                                        FadadaProperties properties,
                                        JdbcTemplate jdbc) {
        this.taskMapper = taskMapper;
        this.contractMapper = contractMapper;
        this.companyMapper = companyMapper;
        this.accessControl = accessControl;
        this.companyService = companyService;
        this.gateway = gateway;
        this.pdfService = pdfService;
        this.archiveService = archiveService;
        this.tradeService = tradeService;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public ContractSigningPayload current(Long contractId) {
        long companyId = AuthContext.requireCompanyId();
        accessControl.requireAnyPermission(companyId, "contract_view", "contract_sign");
        TradeContract contract = requireParty(contractId, companyId);
        return payload(contract, find(contract));
    }

    @Transactional
    public ServiceUrlPayload signUrl(Long contractId) {
        requireReady();
        long companyId = AuthContext.requireCompanyId();
        accessControl.requirePermission(companyId, "contract_sign");
        TradeContract contract = requireParty(contractId, companyId);
        if (!"PENDING".equals(contract.getStatus())) throw new BusinessException("当前合同不在待签署状态");
        FadadaContractSignTask task = prepare(contract);
        task = sync(task, contract);
        String actorId = actorId(task, companyId);
        if (isSigned(actorStatus(task, companyId))) throw new BusinessException("当前企业已完成签署，请等待对方签署");
        String url = gateway.actorUrl(task.getSignTaskId(), actorId,
                "tradepass-user-" + AuthContext.userId(),
                "/pages/service-return/service-return?scene=contract&contractId=" + contractId);
        validateUrl(url);
        return new ServiceUrlPayload(url, "contract", task.getProviderStatus());
    }

    @Transactional
    public ContractSigningPayload syncCurrent(Long contractId) {
        requireReady();
        long companyId = AuthContext.requireCompanyId();
        accessControl.requireAnyPermission(companyId, "contract_view", "contract_sign");
        TradeContract contract = requireParty(contractId, companyId);
        FadadaContractSignTask task = find(contract);
        if (task == null || !hasText(task.getSignTaskId())) return payload(contract, task);
        return payload(contractMapper.selectById(contractId), sync(task, contract));
    }

    @Transactional
    public void syncBySignTaskId(String signTaskId) {
        FadadaContractSignTask task = taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .and(query -> query.eq(FadadaContractSignTask::getSignTaskId, signTaskId)
                        .or().eq(FadadaContractSignTask::getAbolishedSignTaskId, signTaskId))
                .last("LIMIT 1"));
        if (task == null) return;
        TradeContract contract = contractMapper.selectById(task.getContractId());
        if (contract != null) sync(task, contract);
    }

    @Transactional
    public void cancelPending(Long contractId, String reason) {
        TradeContract contract = contractMapper.selectById(contractId);
        if (contract == null) return;
        FadadaContractSignTask task = find(contract);
        if (task == null || !hasText(task.getSignTaskId()) || terminal(task.getProviderStatus())) return;
        gateway.cancel(task.getSignTaskId(), reason);
        task.setProviderStatus("task_terminated");
        task.setLastError(reason);
        taskMapper.updateById(task);
    }

    @Transactional
    public ServiceUrlPayload abolishUrl(Long contractId, String reason) {
        requireReady();
        long companyId = AuthContext.requireCompanyId();
        accessControl.requirePermission(companyId, "contract_sign");
        TradeContract contract = requireParty(contractId, companyId);
        if (!"ACTIVE".equals(contract.getStatus())) throw new BusinessException("仅履约中的合同可以发起作废签署");
        Long approved = jdbc.queryForObject("""
                SELECT COUNT(1) FROM bilateral_action_request
                WHERE contract_id = ? AND biz_type = 'CONTRACT' AND action_type = 'VOID'
                  AND status = 'APPROVED'
                """, Long.class, contractId);
        if (approved == null || approved == 0) {
            throw new BusinessException("请先由双方确认合同作废申请");
        }
        FadadaContractSignTask task = find(contract);
        if (task == null || !hasText(task.getSignTaskId())) throw new BusinessException("该合同没有电子签署记录");
        if (!hasText(task.getAbolishedSignTaskId()) || abolishRetryable(task.getProviderStatus())) {
            task.setAbolishedSignTaskId(gateway.abolish(task.getSignTaskId(), safeReason(reason), properties.getCallbackUrl()));
            task.setProviderStatus("abolishing");
            task.setInitiatorSignStatus(null);
            task.setCounterpartySignStatus(null);
            taskMapper.updateById(task);
        }
        String actorId = actorId(task, companyId);
        String url = gateway.actorUrl(task.getAbolishedSignTaskId(), actorId,
                "tradepass-user-" + AuthContext.userId(),
                "/pages/service-return/service-return?scene=abolish&contractId=" + contractId);
        validateUrl(url);
        return new ServiceUrlPayload(url, "abolish", task.getProviderStatus());
    }

    private FadadaContractSignTask prepare(TradeContract contract) {
        FadadaContractSignTask existing = find(contract);
        if (existing != null && hasText(existing.getSignTaskId())) return existing;
        Company initiator = requireCompany(contract.getCompanyId());
        Company counterparty = requireCompany(contract.getCounterpartyCompanyId());
        FadadaCorpIdentity initiatorIdentity = companyService.requireVerified(initiator.getId());
        FadadaCorpIdentity counterpartyIdentity = companyService.requireVerified(counterparty.getId());
        String initiatorSeal = companyService.enabledSealId(initiator.getId());
        String counterpartySeal = companyService.enabledSealId(counterparty.getId());
        String initiatorActor = "SALE".equalsIgnoreCase(contract.getDirection()) ? "supplier" : "buyer";
        String counterpartyActor = "supplier".equals(initiatorActor) ? "buyer" : "supplier";
        ContractPayload payload = tradeService.contractForElectronicSignature(contract.getId(), contract.getCompanyId());
        byte[] pdf = pdfService.generate(payload);
        String sha256 = FileTypeInspector.sha256(pdf);
        FadadaContractSignTask task = existing == null ? new FadadaContractSignTask() : existing;
        if (existing == null) {
            task.setContractId(contract.getId());
            task.setVersionNo(contract.getVersionNo() == null ? 1 : contract.getVersionNo());
            task.setInitiatorCompanyId(contract.getCompanyId());
            task.setCounterpartyCompanyId(contract.getCounterpartyCompanyId());
            task.setInitiatorActorId(initiatorActor);
            task.setCounterpartyActorId(counterpartyActor);
            task.setProviderStatus("CREATING");
            try {
                taskMapper.insert(task);
            } catch (DuplicateKeyException concurrent) {
                FadadaContractSignTask winner = find(contract);
                if (winner != null && hasText(winner.getSignTaskId())) return winner;
                throw new BusinessException("签署文件正在准备，请稍后重试");
            }
        }
        try {
            FadadaSigningGateway.CreatedTask created = gateway.createTask(new FadadaSigningGateway.CreateTaskCommand(
                    pdf, pdfService.fileName(payload), contract.getName(),
                    "contract-" + contract.getId() + "-v" + task.getVersionNo(),
                    initiatorIdentity.getOpenCorpId(), initiatorActor, initiator.getName(), initiatorSeal,
                    counterpartyIdentity.getOpenCorpId(), counterpartyActor, counterparty.getName(), counterpartySeal,
                    "supplier", "buyer", properties.getCallbackUrl()));
            task.setSignTaskId(created.signTaskId());
            task.setSourceFileId(created.fileId());
            task.setDocId(created.docId());
            task.setSourceSha256(sha256);
            task.setProviderStatus("sign_progress");
            task.setLastError("");
            task.setPreparedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return task;
        } catch (RuntimeException exception) {
            task.setProviderStatus("CREATE_FAILED");
            task.setLastError(shortMessage(exception));
            taskMapper.updateById(task);
            throw exception;
        }
    }

    private FadadaContractSignTask sync(FadadaContractSignTask task, TradeContract contract) {
        String remoteTaskId = hasText(task.getAbolishedSignTaskId())
                ? task.getAbolishedSignTaskId() : task.getSignTaskId();
        FadadaSigningGateway.TaskStatus status = gateway.status(remoteTaskId);
        for (FadadaSigningGateway.ActorStatus actor : status.actors()) {
            if (task.getInitiatorActorId().equals(actor.actorId())) task.setInitiatorSignStatus(actor.signStatus());
            if (task.getCounterpartyActorId().equals(actor.actorId())) task.setCounterpartySignStatus(actor.signStatus());
        }
        if (hasText(task.getAbolishedSignTaskId())) {
            task.setProviderStatus("ABOLISH_" + status.status());
            if ("task_finished".equalsIgnoreCase(status.status()) || "revoked".equalsIgnoreCase(status.status())) {
                tradeService.voidAfterElectronicAbolish(contract.getId(), safeUserId(contract));
                task.setProviderStatus("revoked");
                task.setFinishedAt(LocalDateTime.now());
            }
        } else {
            task.setProviderStatus(status.status());
            if ("task_finished".equalsIgnoreCase(status.status()) && task.getArchivedAt() == null) {
                FadadaCorpIdentity owner = companyService.requireVerified(contract.getCompanyId());
                ContractPayload payload = tradeService.contractForElectronicSignature(contract.getId(), contract.getCompanyId());
                byte[] signed = gateway.downloadSignedPdf(task.getSignTaskId(), owner.getOpenCorpId(),
                        pdfService.fileName(payload));
                archiveService.archiveSignedPdf(payload, signed, task.getSignTaskId(), safeUserId(contract));
                task.setArchivedAt(LocalDateTime.now());
                task.setFinishedAt(LocalDateTime.now());
                tradeService.activateAfterElectronicSignature(contract.getId(), safeUserId(contract));
            }
        }
        task.setLastError("");
        taskMapper.updateById(task);
        return task;
    }

    private ContractSigningPayload payload(TradeContract contract, FadadaContractSignTask task) {
        String status = task == null ? "WAITING_AUTH" : task.getProviderStatus();
        long companyId = AuthContext.companyId() == null ? contract.getCompanyId() : AuthContext.companyId();
        String actorStatus = task == null ? null : actorStatus(task, companyId);
        boolean pending = "PENDING".equals(contract.getStatus());
        boolean active = "ACTIVE".equals(contract.getStatus());
        Long approvedCount = active ? jdbc.queryForObject("""
                SELECT COUNT(1) FROM bilateral_action_request
                WHERE contract_id = ? AND biz_type = 'CONTRACT' AND action_type = 'VOID'
                  AND status = 'APPROVED'
                """, Long.class, contract.getId()) : 0L;
        boolean abolishApproved = approvedCount != null && approvedCount > 0;
        boolean abolishing = active && task != null && hasText(task.getAbolishedSignTaskId())
                && !"revoked".equalsIgnoreCase(status);
        boolean canSign = (pending && !isSigned(actorStatus)
                && (task == null || (!terminal(status) && !hasText(task.getAbolishedSignTaskId()))))
                || (abolishing && !isSigned(actorStatus))
                || (active && abolishApproved && task != null && hasText(task.getSignTaskId())
                    && !hasText(task.getAbolishedSignTaskId()));
        return new ContractSigningPayload(String.valueOf(contract.getId()), status, statusText(status),
                task == null ? null : task.getInitiatorSignStatus(),
                task == null ? null : task.getCounterpartySignStatus(), canSign,
                pending && contract.getCompanyId().equals(companyId),
                active && task != null && hasText(task.getSignTaskId()) && !hasText(task.getAbolishedSignTaskId()),
                abolishApproved,
                task != null && task.getArchivedAt() != null,
                task == null || !hasText(task.getLastError()) ? null : task.getLastError());
    }

    private FadadaContractSignTask find(TradeContract contract) {
        return taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .eq(FadadaContractSignTask::getContractId, contract.getId())
                .eq(FadadaContractSignTask::getVersionNo, contract.getVersionNo() == null ? 1 : contract.getVersionNo())
                .last("LIMIT 1"));
    }

    private TradeContract requireParty(Long contractId, long companyId) {
        TradeContract contract = contractMapper.selectById(contractId);
        if (contract == null || (!Long.valueOf(companyId).equals(contract.getCompanyId())
                && !Long.valueOf(companyId).equals(contract.getCounterpartyCompanyId()))) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    private Company requireCompany(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null) throw new BusinessException("签署企业不存在");
        return company;
    }

    private String actorId(FadadaContractSignTask task, long companyId) {
        if (task.getInitiatorCompanyId().equals(companyId)) return task.getInitiatorActorId();
        if (task.getCounterpartyCompanyId().equals(companyId)) return task.getCounterpartyActorId();
        throw new BusinessException("当前企业不是合同签署方");
    }

    private String actorStatus(FadadaContractSignTask task, long companyId) {
        return task.getInitiatorCompanyId().equals(companyId)
                ? task.getInitiatorSignStatus() : task.getCounterpartySignStatus();
    }

    private boolean isSigned(String status) {
        return "signed".equalsIgnoreCase(status) || "sign_completed".equalsIgnoreCase(status)
                || "completed".equalsIgnoreCase(status);
    }

    private boolean terminal(String status) {
        return status != null && (status.contains("terminated") || status.contains("expired")
                || status.equalsIgnoreCase("task_finished") || status.equalsIgnoreCase("revoked"));
    }

    private boolean abolishRetryable(String status) {
        return status != null && (status.toLowerCase().contains("terminated")
                || status.toLowerCase().contains("expired"));
    }

    private String statusText(String status) {
        if (status == null) return "待准备";
        if (status.startsWith("ABOLISH_") || "abolishing".equals(status)) return "作废签署中";
        return switch (status) {
            case "CREATING" -> "正在准备签署文件";
            case "CREATE_FAILED" -> "签署任务准备失败";
            case "sign_progress", "finish_creation" -> "签署中";
            case "sign_completed" -> "双方已签署，正在归档";
            case "task_finished" -> "已签署并归档";
            case "task_terminated", "expired" -> "签署已终止";
            case "revoked" -> "已作废";
            default -> "待完成企业认证和印章设置";
        };
    }

    private void requireReady() {
        if (!properties.isEnabled() || !hasText(properties.getAppId()) || !hasText(properties.getAppSecret())
                || !hasText(properties.getServerUrl()) || !hasText(properties.getCallbackUrl())) {
            throw new BusinessException("电子签服务尚未配置完整");
        }
    }

    private long safeUserId(TradeContract contract) {
        return contract.getInitiatedBy() == null ? 1L : contract.getInitiatedBy();
    }
    private String safeReason(String reason) { return hasText(reason) ? reason.trim() : "双方协商作废合同"; }
    private String shortMessage(RuntimeException exception) {
        String value = exception.getMessage();
        return hasText(value) ? value.substring(0, Math.min(512, value.length())) : "签署任务创建失败";
    }
    private void validateUrl(String value) {
        if (!hasText(value) || !value.startsWith("https://")) throw new BusinessException("电子签服务地址无效");
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
