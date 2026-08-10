package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.TradeContractMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.tradepass.config.StorageProperties;

@Service
public class ContractAttachmentService {
    public static final String PAYMENT_VOUCHER = "PAYMENT_VOUCHER";
    public static final String INVOICE = "INVOICE";
    public static final String OTHER = "OTHER";

    private final JdbcTemplate jdbc;
    private final TradeContractMapper contractMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;
    private final ReconciliationAccountService reconciliationAccountService;

    @Autowired
    public ContractAttachmentService(JdbcTemplate jdbc,
                                     TradeContractMapper contractMapper,
                                     AccessControlService accessControlService,
                                     AuditLogService auditLogService,
                                     ObjectStorageService objectStorageService,
                                     StorageProperties storageProperties,
                                     ReconciliationAccountService reconciliationAccountService) {
        this.jdbc = jdbc;
        this.contractMapper = contractMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.reconciliationAccountService = reconciliationAccountService;
    }

    ContractAttachmentService(JdbcTemplate jdbc,
                              TradeContractMapper contractMapper,
                              AccessControlService accessControlService,
                              AuditLogService auditLogService) {
        this(jdbc, contractMapper, accessControlService, auditLogService, null, null, null);
    }

    ContractAttachmentService(JdbcTemplate jdbc,
                              TradeContractMapper contractMapper,
                              AccessControlService accessControlService,
                              AuditLogService auditLogService,
                              ObjectStorageService objectStorageService,
                              StorageProperties storageProperties) {
        this(jdbc, contractMapper, accessControlService, auditLogService,
                objectStorageService, storageProperties, null);
    }

    public List<Map<String, Object>> list(Long contractId, String category) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_view", "contract_sign", "reconciliation");
        requireContractParty(contractId, companyId);
        String normalized = normalizeCategory(category);
        return jdbc.query("""
                        SELECT attachment.id, attachment.contract_id, attachment.uploader_company_id,
                               attachment.recipient_company_id, attachment.category, attachment.status,
                               attachment.original_name, attachment.content_type,
                               attachment.file_size, attachment.voucher_date, attachment.voucher_amount,
                               attachment.invoice_no, attachment.invoice_date, attachment.invoice_amount,
                               attachment.confirmed_at, attachment.rejected_reason,
                               attachment.created_by, attachment.created_at,
                               company.name AS uploader_company_name,
                               COALESCE(user.nickname, user.phone, CONCAT('用户', attachment.created_by)) AS uploader_name
                        FROM contract_attachment attachment
                        LEFT JOIN company ON company.id = attachment.uploader_company_id
                        LEFT JOIN sys_user user ON user.id = attachment.created_by
                        WHERE attachment.contract_id = ? AND attachment.category = ?
                        ORDER BY attachment.created_at DESC, attachment.id DESC
                        """, (rs, rowNum) -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", rs.getLong("id"));
                    view.put("contractId", rs.getLong("contract_id"));
                    view.put("uploaderCompanyId", rs.getLong("uploader_company_id"));
                    view.put("recipientCompanyId", rs.getObject("recipient_company_id", Long.class));
                    view.put("uploaderCompanyName", rs.getString("uploader_company_name"));
                    view.put("uploaderName", rs.getString("uploader_name"));
                    view.put("category", rs.getString("category"));
                    String status = rs.getString("status");
                    Long recipientCompanyId = rs.getObject("recipient_company_id", Long.class);
                    boolean canConfirm = "PENDING_CONFIRMATION".equals(status)
                            && Long.valueOf(companyId).equals(recipientCompanyId);
                    view.put("status", status);
                    view.put("statusText", canConfirm ? "待我方确认" : statusText(status));
                    view.put("originalName", rs.getString("original_name"));
                    view.put("contentType", rs.getString("content_type"));
                    view.put("fileSize", rs.getLong("file_size"));
                    view.put("voucherDate", rs.getObject("voucher_date", LocalDate.class));
                    view.put("voucherAmount", rs.getBigDecimal("voucher_amount"));
                    view.put("invoiceNo", safe(rs.getString("invoice_no")));
                    view.put("invoiceDate", rs.getObject("invoice_date", LocalDate.class));
                    view.put("invoiceAmount", rs.getBigDecimal("invoice_amount"));
                    view.put("confirmedAt", rs.getTimestamp("confirmed_at") == null
                            ? null : rs.getTimestamp("confirmed_at").toLocalDateTime());
                    view.put("rejectedReason", safe(rs.getString("rejected_reason")));
                    view.put("canConfirm", canConfirm);
                    view.put("canResubmit", "REJECTED".equals(status)
                            && Long.valueOf(companyId).equals(rs.getLong("uploader_company_id")));
                    view.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
                    return view;
                }, contractId, normalized);
    }

    @Transactional
    public Map<String, Object> upload(Long contractId, String category, String originalName,
                                      byte[] data, String voucherDate, String voucherAmount) {
        return upload(contractId, category, originalName, data, voucherDate, voucherAmount,
                null, null, null);
    }

    @Transactional
    public Map<String, Object> upload(Long contractId, String category, String originalName,
                                      byte[] data, String voucherDate, String voucherAmount,
                                      String invoiceNo, String invoiceDate, String invoiceAmount) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_attachment_upload", "contract_sign", "order_create", "reconciliation");
        TradeContract contract = requireContractParty(contractId, companyId);
        String normalized = normalizeCategory(category);
        String contentType = FileTypeInspector.inspect(data);
        validateContentType(normalized, contentType, originalName);
        LocalDate parsedDate = parseDate(voucherDate);
        BigDecimal parsedAmount = parseAmount(voucherAmount);
        if (PAYMENT_VOUCHER.equals(normalized) && parsedAmount == null) {
            throw new BusinessException("请输入转款金额");
        }
        if (PAYMENT_VOUCHER.equals(normalized) && parsedDate == null) {
            throw new BusinessException("请选择转款日期");
        }
        String safeInvoiceNo = invoiceNo == null ? "" : invoiceNo.trim();
        LocalDate parsedInvoiceDate = parseInvoiceDate(invoiceDate);
        BigDecimal parsedInvoiceAmount = parseInvoiceAmount(invoiceAmount);
        if (INVOICE.equals(normalized)) {
            if (safeInvoiceNo.isBlank() || safeInvoiceNo.length() > 128) {
                throw new BusinessException("请输入发票号码且不能超过 128 字");
            }
            if (parsedInvoiceDate == null) throw new BusinessException("请选择开票日期");
            if (parsedInvoiceAmount == null) throw new BusinessException("请输入发票金额");
        }
        Long recipientCompanyId = Long.valueOf(companyId).equals(contract.getCompanyId())
                ? contract.getCounterpartyCompanyId() : contract.getCompanyId();
        if (recipientCompanyId == null) throw new BusinessException("合同对方企业信息不完整");
        String status = OTHER.equals(normalized) ? "APPROVED" : "PENDING_CONFIRMATION";
        String safeName = FileTypeInspector.sanitizeFileName(originalName, contentType);
        String sha256 = FileTypeInspector.sha256(data);
        ObjectStorageService.StoredObject stored = store(companyId, contractId, normalized,
                contentType, data, sha256);
        if (stored == null) {
            jdbc.update("""
                    INSERT INTO contract_attachment
                    (contract_id, uploader_company_id, recipient_company_id, category, status,
                     original_name, content_type, file_size, file_data, sha256, voucher_date,
                     voucher_amount, invoice_no, invoice_date, invoice_amount, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, contractId, companyId, recipientCompanyId, normalized, status,
                    safeName, contentType, data.length, data, sha256, parsedDate, parsedAmount,
                    emptyToNull(safeInvoiceNo), parsedInvoiceDate, parsedInvoiceAmount, AuthContext.userId());
        } else {
            jdbc.update("""
                    INSERT INTO contract_attachment
                    (contract_id, uploader_company_id, recipient_company_id, category, status,
                     original_name, content_type, file_size, file_data, sha256, storage_provider,
                     storage_bucket, object_key, object_version_id, etag, encryption_algorithm,
                     voucher_date, voucher_amount, invoice_no, invoice_date, invoice_amount, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, contractId, companyId, recipientCompanyId, normalized, status,
                    safeName, contentType, data.length,
                    sha256, stored.provider(), stored.bucket(), stored.objectKey(), stored.versionId(),
                    stored.etag(), stored.encryptionAlgorithm(), parsedDate, parsedAmount,
                    emptyToNull(safeInvoiceNo), parsedInvoiceDate, parsedInvoiceAmount, AuthContext.userId());
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditLogService.log(companyId, "CONTRACT_ATTACHMENT", id,
                "UPLOAD", (OTHER.equals(normalized) ? "上传" : "提交待确认")
                        + categoryLabel(normalized) + " " + safeName);
        return list(contractId, normalized).stream()
                .filter(item -> id != null && id.equals(item.get("id")))
                .findFirst().orElseThrow(() -> new BusinessException("附件保存失败"));
    }

    @Transactional
    public Map<String, Object> decide(Long id, String decision, String reason) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_attachment_upload", "reconciliation", "invoice_view");
        AttachmentRecord attachment = requireAttachment(id);
        requireContractParty(attachment.contractId(), companyId);
        if (!Long.valueOf(companyId).equals(attachment.recipientCompanyId())) {
            throw new BusinessException("仅接收方企业可以确认该资料");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(normalized) && !"REJECT".equals(normalized)) {
            throw new BusinessException("确认结果不正确");
        }
        if ("APPROVED".equals(attachment.status())) {
            return findView(attachment.contractId(), attachment.category(), id);
        }
        if (!"PENDING_CONFIRMATION".equals(attachment.status())) {
            throw new BusinessException("资料当前状态不能确认");
        }

        if ("REJECT".equals(normalized)) {
            String safeReason = reason == null ? "" : reason.trim();
            if (safeReason.isBlank() || safeReason.length() > 500) {
                throw new BusinessException("请输入驳回原因且不能超过 500 字");
            }
            jdbc.update("""
                    UPDATE contract_attachment
                    SET status = 'REJECTED', confirmed_by = ?, confirmed_at = CURRENT_TIMESTAMP,
                        rejected_reason = ?
                    WHERE id = ? AND status = 'PENDING_CONFIRMATION'
                    """, AuthContext.userId(), safeReason, id);
            auditLogService.log(companyId, "CONTRACT_ATTACHMENT", id,
                    "REJECT", "驳回" + categoryLabel(attachment.category()) + "：" + safeReason);
            return findView(attachment.contractId(), attachment.category(), id);
        }

        validateApprovalMetadata(attachment);
        LocalDateTime confirmedAt = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE contract_attachment
                SET status = 'APPROVED', confirmed_by = ?, confirmed_at = ?, rejected_reason = NULL
                WHERE id = ? AND status = 'PENDING_CONFIRMATION'
                """, AuthContext.userId(), confirmedAt, id);
        if (updated == 0) return findView(attachment.contractId(), attachment.category(), id);
        if (reconciliationAccountService != null) {
            TradeContract contract = requireContractParty(attachment.contractId(), companyId);
            boolean invoice = INVOICE.equals(attachment.category());
            reconciliationAccountService.recordAttachment(contract, attachment.category(), id,
                    invoice ? attachment.invoiceDate() : attachment.voucherDate(),
                    invoice ? attachment.invoiceNo() : attachment.originalName(),
                    invoice ? attachment.invoiceAmount() : attachment.voucherAmount(),
                    attachment.uploaderCompanyId(), AuthContext.userId(), confirmedAt);
        }
        auditLogService.log(companyId, "CONTRACT_ATTACHMENT", id,
                "APPROVE", "确认" + categoryLabel(attachment.category()) + " " + attachment.originalName());
        return findView(attachment.contractId(), attachment.category(), id);
    }

    public FilePayload getFile(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_view", "contract_sign", "reconciliation");
        List<FilePayload> files = jdbc.query("""
                        SELECT id, contract_id, original_name, content_type, file_size, file_data, sha256,
                               storage_bucket, object_key, object_version_id
                        FROM contract_attachment WHERE id = ?
                        """, (rs, rowNum) -> new FilePayload(
                        rs.getLong("id"), rs.getLong("contract_id"),
                        rs.getString("original_name"), rs.getString("content_type"),
                        rs.getBytes("file_data"), rs.getString("storage_bucket"),
                        rs.getString("object_key"), rs.getString("object_version_id"),
                        rs.getLong("file_size"), rs.getString("sha256")), id);
        if (files.isEmpty()) throw new BusinessException("附件不存在");
        FilePayload payload = files.get(0);
        requireContractParty(payload.contractId(), companyId);
        if (payload.data() != null) return payload;
        if (objectStorageService == null || !objectStorageService.isEnabled()
                || payload.objectKey() == null) {
            throw new BusinessException("附件内容暂不可用，请联系管理员");
        }
        byte[] data = objectStorageService.get(new ObjectStorageService.ObjectReference(
                payload.storageBucket(), payload.objectKey(), payload.objectVersionId(),
                payload.fileSize(), payload.sha256()));
        return payload.withData(data);
    }

    private ObjectStorageService.StoredObject store(long companyId, Long contractId, String category,
                                                     String contentType, byte[] data, String sha256) {
        if (objectStorageService == null || !objectStorageService.isEnabled()) return null;
        LocalDate today = LocalDate.now();
        String fileType = switch (category) {
            case PAYMENT_VOUCHER -> "payment-voucher";
            case INVOICE -> "invoice";
            default -> "attachment";
        };
        String key = keyPrefix() + "/file/" + companyId + "/" + contractId + "/"
                + fileType + "/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + UUID.randomUUID() + "-" + sha256 + "."
                + FileTypeInspector.extension(contentType);
        return objectStorageService.putImmutable(key, data, contentType, sha256);
    }

    private String keyPrefix() {
        String value = storageProperties == null ? "tradepass" : storageProperties.getKeyPrefix();
        value = value == null ? "" : value.trim().replaceAll("^/+|/+$", "");
        return value.isBlank() ? "tradepass" : value;
    }

    private TradeContract requireContractParty(Long contractId, long companyId) {
        TradeContract contract = contractMapper.selectById(contractId);
        if (contract == null || (!Long.valueOf(companyId).equals(contract.getCompanyId())
                && !Long.valueOf(companyId).equals(contract.getCounterpartyCompanyId()))) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_VOUCHER.equals(normalized)
                && !INVOICE.equals(normalized)
                && !OTHER.equals(normalized)) {
            throw new BusinessException("附件分类不正确");
        }
        return normalized;
    }

    private void validateContentType(String category, String contentType, String originalName) {
        if ((PAYMENT_VOUCHER.equals(category) || INVOICE.equals(category))
                && !FileTypeInspector.isImage(contentType)
                && !"application/pdf".equals(contentType)) {
            throw new BusinessException(categoryLabel(category) + "仅支持图片或 PDF");
        }
        if (OTHER.equals(category)
                && !FileTypeInspector.isImage(contentType)
                && !"application/pdf".equals(contentType)
                && !FileTypeInspector.isWord(contentType)) {
            throw new BusinessException("其它资料仅支持图片、PDF 或 Word");
        }
        if ("application/msword".equals(contentType)
                && (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".doc"))) {
            throw new BusinessException("旧版 Word 文件扩展名必须为 .doc");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw new BusinessException("转款日期格式不正确");
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            if (amount.signum() < 0) throw new NumberFormatException();
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception exception) {
            throw new BusinessException("转款金额格式不正确");
        }
    }

    private LocalDate parseInvoiceDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw new BusinessException("开票日期格式不正确");
        }
    }

    private BigDecimal parseInvoiceAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            if (amount.signum() < 0) throw new NumberFormatException();
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception exception) {
            throw new BusinessException("发票金额格式不正确");
        }
    }

    private AttachmentRecord requireAttachment(Long id) {
        List<AttachmentRecord> rows = jdbc.query("""
                        SELECT id, contract_id, uploader_company_id, recipient_company_id,
                               category, status, original_name, voucher_date, voucher_amount,
                               invoice_no, invoice_date, invoice_amount
                        FROM contract_attachment WHERE id = ?
                        """, (rs, rowNum) -> new AttachmentRecord(
                        rs.getLong("id"), rs.getLong("contract_id"),
                        rs.getLong("uploader_company_id"),
                        rs.getObject("recipient_company_id", Long.class),
                        rs.getString("category"), rs.getString("status"),
                        rs.getString("original_name"),
                        rs.getObject("voucher_date", LocalDate.class),
                        rs.getBigDecimal("voucher_amount"), rs.getString("invoice_no"),
                        rs.getObject("invoice_date", LocalDate.class),
                        rs.getBigDecimal("invoice_amount")), id);
        if (rows.isEmpty()) throw new BusinessException("附件不存在");
        return rows.get(0);
    }

    private void validateApprovalMetadata(AttachmentRecord attachment) {
        if (PAYMENT_VOUCHER.equals(attachment.category())
                && (attachment.voucherDate() == null || attachment.voucherAmount() == null)) {
            throw new BusinessException("转款凭证金额或日期不完整，请上传方重新提交");
        }
        if (INVOICE.equals(attachment.category())
                && (attachment.invoiceNo() == null || attachment.invoiceNo().isBlank()
                || attachment.invoiceDate() == null || attachment.invoiceAmount() == null)) {
            throw new BusinessException("发票号码、日期或金额不完整，请上传方重新提交");
        }
    }

    private Map<String, Object> findView(Long contractId, String category, Long id) {
        return list(contractId, category).stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst().orElseThrow(() -> new BusinessException("附件不存在"));
    }

    private String statusText(String status) {
        if ("PENDING_CONFIRMATION".equals(status)) return "待对方确认";
        if ("APPROVED".equals(status)) return "已通过";
        if ("REJECTED".equals(status)) return "已驳回";
        if ("LEGACY".equals(status)) return "历史资料（未计入对账）";
        return status == null ? "" : status;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case PAYMENT_VOUCHER -> "转款凭证";
            case INVOICE -> "发票";
            default -> "其它资料";
        };
    }

    private record AttachmentRecord(Long id, Long contractId, Long uploaderCompanyId,
                                    Long recipientCompanyId, String category, String status,
                                    String originalName, LocalDate voucherDate,
                                    BigDecimal voucherAmount, String invoiceNo,
                                    LocalDate invoiceDate, BigDecimal invoiceAmount) {
    }

    public record FilePayload(Long id, Long contractId, String originalName, String contentType,
                              byte[] data, String storageBucket, String objectKey,
                              String objectVersionId, Long fileSize, String sha256) {
        public FilePayload(Long id, Long contractId, String originalName,
                           String contentType, byte[] data) {
            this(id, contractId, originalName, contentType, data,
                    null, null, null, data == null ? null : (long) data.length,
                    data == null ? null : FileTypeInspector.sha256(data));
        }

        FilePayload withData(byte[] value) {
            return new FilePayload(id, contractId, originalName, contentType, value,
                    storageBucket, objectKey, objectVersionId, fileSize, sha256);
        }
    }
}
