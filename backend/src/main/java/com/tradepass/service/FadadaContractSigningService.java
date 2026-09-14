package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.Duration;
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
    private ContractAbolishIntentService abolishIntents;

    @Autowired
    void setAbolishIntentService(ContractAbolishIntentService service) { this.abolishIntents = service; }
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BoundedBinaryCache signedPreviewCache =
            new BoundedBinaryCache(Duration.ofMinutes(30), 64L * 1024 * 1024);

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
        TradeContract contract = requireParty(contractId, companyId, true);
        if (!"PENDING".equals(contract.getStatus())) throw new BusinessException("当前合同不在待签署状态");
        FadadaContractSignTask task = contract.getCompanyId().equals(companyId) ? prepare(contract) : find(contract, true);
        if (task == null || !hasText(task.getSignTaskId())) {
            throw new BusinessException("请等待合同发起方先完成签署");
        }
        task = sync(task, contract);
        String actorId = actorId(task, companyId);
        if (isSigned(actorStatus(task, companyId))) throw new BusinessException("当前企业已完成签署，请等待对方签署");
        if (terminal(task.getProviderStatus())) throw new BusinessException("签署任务已结束，请刷新合同状态");
        if (!contract.getCompanyId().equals(companyId) && !isSigned(task.getInitiatorSignStatus())) {
            throw new BusinessException("请等待合同发起方先完成签署");
        }
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
        TradeContract contract = requireParty(contractId, companyId, true);
        FadadaContractSignTask task = find(contract, true);
        if (task == null || !hasText(task.getSignTaskId())) return payload(contract, task);
        task = sync(task, contract);
        return payload(contractMapper.selectByIdForUpdate(contractId), task);
    }

    public SignedPreview signedPreview(Long contractId) {
        requireReady();
        long companyId = AuthContext.requireCompanyId();
        accessControl.requireAnyPermission(companyId, "contract_view", "contract_sign");
        TradeContract contract = requireParty(contractId, companyId);
        FadadaContractSignTask task = find(contract);
        if (task == null || !hasText(task.getSignTaskId()) || task.getArchivedAt() == null) {
            throw new BusinessException("合同真实签章页尚未归档");
        }
        FadadaCorpIdentity owner = companyService.requireVerified(contract.getCompanyId());
        byte[] image = signedPreviewCache.get(
                "signed-preview:" + task.getSignTaskId(),
                () -> gateway.downloadSignedPreviewPage(task.getSignTaskId(), owner.getOpenCorpId()));
        return new SignedPreview("合同签章页.png", image);
    }

    @Transactional
    public void syncBySignTaskId(String signTaskId) {
        FadadaContractSignTask task = taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .and(query -> query.eq(FadadaContractSignTask::getSignTaskId, signTaskId)
                        .or().eq(FadadaContractSignTask::getAbolishedSignTaskId, signTaskId))
                .last("LIMIT 1"));
        if (task == null) {
            if (syncCancelledAbolishTask(signTaskId, false)) return;
            if (recoverUnknownAbolishCallback(signTaskId)) return;
            throw new BusinessException("签署任务尚未就绪，请稍后重试");
        }
        TradeContract contract = contractMapper.selectByIdForUpdate(task.getContractId());
        if (contract == null || version(contract) != taskVersion(task)) return;
        // Locking read refreshes task state after another callback committed.
        task = taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .eq(FadadaContractSignTask::getId, task.getId()).last("LIMIT 1 FOR UPDATE"));
        if (task != null && !signTaskId.equals(task.getSignTaskId())
                && !signTaskId.equals(task.getAbolishedSignTaskId())) {
            if (!syncCancelledAbolishTask(signTaskId, true)) throw new BusinessException("签署任务关联状态已变化，请稍后重试");
        } else if (task != null) {
            sync(task, contract);
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ServiceUrlPayload abolishUrl(Long contractId, String reason) {
        requireReady();
        long companyId = AuthContext.requireCompanyId();
        accessControl.requirePermission(companyId, "contract_sign");
        TradeContract contract = requireParty(contractId, companyId, true);
        if (!"ACTIVE".equals(contract.getStatus())) throw new BusinessException("仅履约中的合同可以发起作废签署");
        var approved = jdbc.queryForList("""
                SELECT id FROM bilateral_action_request
                WHERE contract_id = ? AND biz_type = 'CONTRACT' AND action_type = 'VOID'
                  AND status = 'APPROVED' FOR UPDATE
                """, Long.class, contractId);
        if (approved.isEmpty()) {
            throw new BusinessException("请先由双方确认合同作废申请");
        }
        var recovering = jdbc.queryForList("""
                SELECT id FROM bilateral_action_request
                WHERE contract_id = ? AND action_type = 'RESUME' AND status = 'PENDING'
                FOR UPDATE
                """, Long.class, contractId);
        if (!recovering.isEmpty()) throw new BusinessException("双方正在确认恢复履约，请先处理恢复申请");
        FadadaContractSignTask task = find(contract, true);
        if (task == null || !hasText(task.getSignTaskId())) throw new BusinessException("该合同没有电子签署记录");
        reconcileAbolishIntent(task);
        if (!hasText(task.getAbolishedSignTaskId()) || abolishRetryable(task.getProviderStatus())) {
            if (hasText(task.getAbolishedSignTaskId())) {
                var oldStatus = gateway.status(task.getAbolishedSignTaskId());
                if (oldStatus == null || !ContractAbolishRecoveryService.stopped(oldStatus.status())) {
                    throw new BusinessException("上一作废任务尚未确认终止，请先同步状态");
                }
                jdbc.update("""
                        INSERT IGNORE INTO fadada_cancelled_abolish_task
                        (sign_task_id, contract_id, version_no, original_sign_task_id)
                        VALUES (?, ?, ?, ?)
                        """, task.getAbolishedSignTaskId(), contractId, version(contract), task.getSignTaskId());
            }
            if (abolishIntents == null) throw new BusinessException("作废任务安全记录服务不可用，请稍后重试");
            String intentId = abolishIntents.begin(task);
            String previousChildId = task.getAbolishedSignTaskId();
            try {
                String abolishId = gateway.abolish(task.getSignTaskId(), safeReason(reason), properties.getCallbackUrl());
                if (!hasText(abolishId) || abolishId.equals(previousChildId)) throw new BusinessException("未返回新的作废任务编号");
                task.setAbolishedSignTaskId(abolishId);
            } catch (RuntimeException exception) {
                task.setProviderStatus("ABOLISH_CREATION_UNCERTAIN");
                task.setLastError(shortMessage(exception));
                taskMapper.updateById(task);
                throw new BusinessException("作废任务创建结果尚未确认，合同保持只读，请核实电子签记录后处理");
            }
            task.setProviderStatus("abolishing");
            task.setInitiatorSignStatus(null);
            task.setCounterpartySignStatus(null);
            taskMapper.update(task, new LambdaUpdateWrapper<FadadaContractSignTask>()
                    .eq(FadadaContractSignTask::getId, task.getId())
                    .set(FadadaContractSignTask::getInitiatorSignStatus, null)
                    .set(FadadaContractSignTask::getCounterpartySignStatus, null));
            abolishIntents.confirm(intentId, task.getAbolishedSignTaskId());
        }
        String actorId = actorId(task, companyId);
        String url;
        try {
            url = gateway.actorUrl(task.getAbolishedSignTaskId(), actorId,
                    "tradepass-user-" + AuthContext.userId(),
                    "/pages/service-return/service-return?scene=abolish&contractId=" + contractId);
        } catch (RuntimeException exception) {
            // Keep the successfully created provider id even when loading its page fails.
            throw new BusinessException("作废任务已保留，签署页面暂时无法打开，请稍后重试");
        }
        validateUrl(url);
        return new ServiceUrlPayload(url, "abolish", task.getProviderStatus());
    }

    private FadadaContractSignTask prepare(TradeContract contract) {
        FadadaContractSignTask existing = find(contract, true);
        if (existing != null && hasText(existing.getSignTaskId())) return existing;
        Company initiator = requireCompany(contract.getCompanyId());
        Company counterparty = requireCompany(contract.getCounterpartyCompanyId());
        FadadaCorpIdentity initiatorIdentity = companyService.requireVerified(initiator.getId());
        FadadaCorpIdentity counterpartyIdentity = companyService.requireVerified(counterparty.getId());
        String initiatorSeal = companyService.enabledSealId(initiator.getId());
        String counterpartySeal = companyService.enabledSealId(counterparty.getId());
        String initiatorActor = "SALE".equalsIgnoreCase(contract.getDirection()) ? "supplier" : "buyer";
        String counterpartyActor = "supplier".equals(initiatorActor) ? "buyer" : "supplier";
        ContractPayload payload = signingSnapshot(contract, existing);
        byte[] pdf = pdfService.generate(payload);
        String sha256 = FileTypeInspector.sha256(pdf);
        FadadaContractSignTask task = existing == null ? new FadadaContractSignTask() : existing;
        task.setContractSnapshot(serializeSnapshot(payload));
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
                FadadaContractSignTask winner = find(contract, true);
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
        return sync(task, contract, true);
    }

    private FadadaContractSignTask sync(FadadaContractSignTask task, TradeContract contract, boolean reconcileIntent) {
        if (!contract.getId().equals(task.getContractId()) || version(contract) != taskVersion(task)) return task;
        if (!"PENDING".equals(contract.getStatus()) && !"ACTIVE".equals(contract.getStatus())) return task;
        if (reconcileIntent) reconcileAbolishIntent(task);
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
                tradeService.voidAfterElectronicAbolish(contract.getId(), taskVersion(task), safeUserId(contract));
                task.setProviderStatus("revoked");
                task.setFinishedAt(LocalDateTime.now());
            }
        } else {
            task.setProviderStatus(status.status());
            if ("task_finished".equalsIgnoreCase(status.status()) && task.getArchivedAt() == null) {
                FadadaCorpIdentity owner = companyService.requireVerified(contract.getCompanyId());
                ContractPayload payload = signingSnapshot(contract, task);
                task.setContractSnapshot(serializeSnapshot(payload));
                byte[] signed = gateway.downloadSignedPdf(task.getSignTaskId(), owner.getOpenCorpId(),
                        pdfService.fileName(payload));
                archiveService.archiveSignedPdf(payload, signed, task.getSignTaskId(), safeUserId(contract));
                task.setArchivedAt(LocalDateTime.now());
                task.setFinishedAt(LocalDateTime.now());
                tradeService.activateAfterElectronicSignature(contract.getId(), taskVersion(task), safeUserId(contract));
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
        Long recoveryCount = active ? jdbc.queryForObject("""
                SELECT COUNT(1) FROM bilateral_action_request
                WHERE contract_id = ? AND biz_type = 'CONTRACT' AND action_type = 'RESUME'
                  AND status = 'PENDING'
                """, Long.class, contract.getId()) : 0L;
        boolean recoveryPending = recoveryCount != null && recoveryCount > 0;
        boolean abolishing = active && task != null && hasText(task.getAbolishedSignTaskId())
                && !"revoked".equalsIgnoreCase(status);
        boolean firstSignerReady = contract.getCompanyId().equals(companyId)
                || task != null && isSigned(task.getInitiatorSignStatus());
        boolean canSign = (pending && firstSignerReady && !isSigned(actorStatus)
                && (task == null || (!terminal(status) && !hasText(task.getAbolishedSignTaskId()))))
                || (abolishing && !isSigned(actorStatus))
                || (active && abolishApproved && task != null && hasText(task.getSignTaskId())
                    && !hasText(task.getAbolishedSignTaskId()));
        canSign = canSign && !recoveryPending && !"ABOLISH_CREATION_UNCERTAIN".equals(status);
        String displayStatus = pending && !firstSignerReady ? "等待发起方签署" : statusText(status);
        return new ContractSigningPayload(String.valueOf(contract.getId()), status, displayStatus,
                task == null ? null : task.getInitiatorSignStatus(),
                task == null ? null : task.getCounterpartySignStatus(), canSign,
                pending && contract.getCompanyId().equals(companyId),
                active && task != null && hasText(task.getSignTaskId()) && !hasText(task.getAbolishedSignTaskId()),
                abolishApproved,
                task != null && task.getArchivedAt() != null,
                task == null || !hasText(task.getLastError()) ? null : task.getLastError());
    }

    private int version(TradeContract contract) {
        return contract.getVersionNo() == null ? 1 : contract.getVersionNo();
    }

    private boolean syncCancelledAbolishTask(String signTaskId, boolean contractLocked) {
        var rows = jdbc.query("""
                SELECT contract_id, version_no FROM fadada_cancelled_abolish_task WHERE sign_task_id = ?
                """ + (contractLocked ? " FOR UPDATE" : ""),
                (rs, row) -> new long[]{rs.getLong("contract_id"), rs.getLong("version_no")}, signTaskId);
        if (rows.isEmpty()) return false;
        TradeContract contract = contractMapper.selectByIdForUpdate(rows.get(0)[0]);
        if (contract == null || version(contract) != rows.get(0)[1]) return true;
        FadadaSigningGateway.TaskStatus remote = gateway.status(signTaskId);
        if (remote == null || !hasText(remote.status())) throw new BusinessException("无法核实已取消作废任务的状态");
        if ("task_finished".equalsIgnoreCase(remote.status()) || "revoked".equalsIgnoreCase(remote.status())) {
            // A real completion arriving after recovery wins over local recovery state.
            tradeService.voidAfterElectronicAbolish(contract.getId(), version(contract), safeUserId(contract));
            taskMapper.update(new LambdaUpdateWrapper<FadadaContractSignTask>()
                    .eq(FadadaContractSignTask::getContractId, contract.getId())
                    .eq(FadadaContractSignTask::getVersionNo, version(contract))
                    .set(FadadaContractSignTask::getProviderStatus, "revoked")
                    .set(FadadaContractSignTask::getFinishedAt, LocalDateTime.now()));
        } else if (!ContractAbolishRecoveryService.stopped(remote.status())) {
            throw new BusinessException("已取消作废任务的远程状态发生变化，需要核实处理");
        }
        return true;
    }

    private int taskVersion(FadadaContractSignTask task) {
        return task.getVersionNo() == null ? 1 : task.getVersionNo();
    }

    private ContractPayload signingSnapshot(TradeContract contract, FadadaContractSignTask task) {
        try {
            ContractPayload snapshot = task != null && hasText(task.getContractSnapshot())
                    ? objectMapper.readValue(task.getContractSnapshot(), ContractPayload.class)
                    : tradeService.contractForElectronicSignature(contract.getId(), contract.getCompanyId());
            if (snapshot == null || !String.valueOf(contract.getId()).equals(snapshot.id())
                    || snapshot.versionNo() == null || snapshot.versionNo() != version(contract)) {
                throw new BusinessException("签署快照与合同版本不一致");
            }
            return snapshot;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("签署合同快照读取失败");
        }
    }

    private String serializeSnapshot(ContractPayload snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (Exception exception) { throw new BusinessException("签署合同快照保存失败"); }
    }

    private FadadaContractSignTask find(TradeContract contract) {
        return find(contract, false);
    }

    private FadadaContractSignTask find(TradeContract contract, boolean lock) {
        return taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .eq(FadadaContractSignTask::getContractId, contract.getId())
                .eq(FadadaContractSignTask::getVersionNo, contract.getVersionNo() == null ? 1 : contract.getVersionNo())
                .last(lock ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"));
    }

    private void reconcileAbolishIntent(FadadaContractSignTask task) {
        var pending = abolishIntents == null ? List.<ContractAbolishIntentService.PendingIntent>of()
                : abolishIntents.pending(task);
        if (pending.isEmpty() && !"ABOLISH_CREATION_UNCERTAIN".equals(task.getProviderStatus())) return;
        var parent = gateway.status(task.getSignTaskId());
        String childId = parent == null ? null : parent.abolishedSignTaskId();
        if (!hasText(childId) || pending.stream().anyMatch(item -> childId.equals(item.previousTaskId()))) {
            throw new BusinessException("作废任务创建结果尚未确认，请稍后刷新签署状态或核实电子签记录");
        }
        var child = gateway.status(childId);
        if (child == null || !task.getSignTaskId().equals(child.originalSignTaskId())
                || !childId.equals(child.signTaskId())) {
            throw new BusinessException("无法确认作废协议与原合同的对应关系，合同保持只读");
        }
        task.setAbolishedSignTaskId(childId);
        task.setProviderStatus("abolishing");
        task.setInitiatorSignStatus(null);
        task.setCounterpartySignStatus(null);
        task.setLastError("");
        taskMapper.update(task, new LambdaUpdateWrapper<FadadaContractSignTask>()
                .eq(FadadaContractSignTask::getId, task.getId())
                .set(FadadaContractSignTask::getInitiatorSignStatus, null)
                .set(FadadaContractSignTask::getCounterpartySignStatus, null));
        for (var intent : pending) abolishIntents.confirm(intent.id(), childId);
    }

    private boolean recoverUnknownAbolishCallback(String childId) {
        if (abolishIntents == null) return false;
        var child = gateway.status(childId);
        if (child == null || !hasText(child.originalSignTaskId()) || !childId.equals(child.signTaskId())) return false;
        var task = taskMapper.selectOne(new LambdaQueryWrapper<FadadaContractSignTask>()
                .eq(FadadaContractSignTask::getSignTaskId, child.originalSignTaskId()).last("LIMIT 1"));
        if (task == null) return false;
        var contract = contractMapper.selectByIdForUpdate(task.getContractId());
        if (contract == null || version(contract) != taskVersion(task)) return false;
        task = find(contract, true);
        if (task == null || abolishIntents.pending(task).isEmpty()) return false;
        reconcileAbolishIntent(task);
        if (!childId.equals(task.getAbolishedSignTaskId())) return false;
        sync(task, contract, false);
        return true;
    }

    private TradeContract requireParty(Long contractId, long companyId) {
        return requireParty(contractId, companyId, false);
    }

    private TradeContract requireParty(Long contractId, long companyId, boolean lock) {
        TradeContract contract = lock ? contractMapper.selectByIdForUpdate(contractId)
                : contractMapper.selectById(contractId);
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
        return status != null && (status.toLowerCase(java.util.Locale.ROOT).contains("terminated")
                || status.toLowerCase(java.util.Locale.ROOT).contains("expired")
                || status.equalsIgnoreCase("task_finished") || status.equalsIgnoreCase("revoked"));
    }

    private boolean abolishRetryable(String status) {
        return status != null && (status.toLowerCase().contains("terminated")
                || status.toLowerCase().contains("expired"));
    }

    private String statusText(String status) {
        if (status == null) return "待准备";
        if ("ABOLISH_CREATION_UNCERTAIN".equals(status)) return "作废任务结果待核实";
        if (status.startsWith("ABOLISH_") && abolishRetryable(status)) return "作废签署已中止，可重试或申请恢复履约";
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

    public record SignedPreview(String fileName, byte[] data) {
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
