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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.tradepass.config.OssProperties;

@Service
public class ContractAttachmentService {
    public static final String PAYMENT_VOUCHER = "PAYMENT_VOUCHER";
    public static final String OTHER = "OTHER";

    private final JdbcTemplate jdbc;
    private final TradeContractMapper contractMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectStorageService objectStorageService;
    private final OssProperties ossProperties;

    @Autowired
    public ContractAttachmentService(JdbcTemplate jdbc,
                                     TradeContractMapper contractMapper,
                                     AccessControlService accessControlService,
                                     AuditLogService auditLogService,
                                     ObjectStorageService objectStorageService,
                                     OssProperties ossProperties) {
        this.jdbc = jdbc;
        this.contractMapper = contractMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.objectStorageService = objectStorageService;
        this.ossProperties = ossProperties;
    }

    ContractAttachmentService(JdbcTemplate jdbc,
                              TradeContractMapper contractMapper,
                              AccessControlService accessControlService,
                              AuditLogService auditLogService) {
        this(jdbc, contractMapper, accessControlService, auditLogService, null, null);
    }

    public List<Map<String, Object>> list(Long contractId, String category) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_view", "contract_sign", "reconciliation");
        requireContractParty(contractId, companyId);
        String normalized = normalizeCategory(category);
        return jdbc.query("""
                        SELECT attachment.id, attachment.contract_id, attachment.uploader_company_id,
                               attachment.category, attachment.original_name, attachment.content_type,
                               attachment.file_size, attachment.voucher_date, attachment.voucher_amount,
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
                    view.put("uploaderCompanyName", rs.getString("uploader_company_name"));
                    view.put("uploaderName", rs.getString("uploader_name"));
                    view.put("category", rs.getString("category"));
                    view.put("originalName", rs.getString("original_name"));
                    view.put("contentType", rs.getString("content_type"));
                    view.put("fileSize", rs.getLong("file_size"));
                    view.put("voucherDate", rs.getObject("voucher_date", LocalDate.class));
                    view.put("voucherAmount", rs.getBigDecimal("voucher_amount"));
                    view.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
                    return view;
                }, contractId, normalized);
    }

    @Transactional
    public Map<String, Object> upload(Long contractId, String category, String originalName,
                                      byte[] data, String voucherDate, String voucherAmount) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_attachment_upload", "contract_sign", "order_create", "reconciliation");
        requireContractParty(contractId, companyId);
        String normalized = normalizeCategory(category);
        String contentType = FileTypeInspector.inspect(data);
        validateContentType(normalized, contentType, originalName);
        LocalDate parsedDate = parseDate(voucherDate);
        BigDecimal parsedAmount = parseAmount(voucherAmount);
        String safeName = FileTypeInspector.sanitizeFileName(originalName, contentType);
        String sha256 = FileTypeInspector.sha256(data);
        ObjectStorageService.StoredObject stored = store(companyId, contractId, normalized,
                contentType, data, sha256);
        if (stored == null) {
            jdbc.update("""
                    INSERT INTO contract_attachment
                    (contract_id, uploader_company_id, category, original_name, content_type,
                     file_size, file_data, sha256, voucher_date, voucher_amount, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, contractId, companyId, normalized, safeName, contentType,
                    data.length, data, sha256, parsedDate, parsedAmount, AuthContext.userId());
        } else {
            jdbc.update("""
                    INSERT INTO contract_attachment
                    (contract_id, uploader_company_id, category, original_name, content_type,
                     file_size, file_data, sha256, storage_provider, storage_bucket, object_key,
                     object_version_id, etag, encryption_algorithm, voucher_date, voucher_amount, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, contractId, companyId, normalized, safeName, contentType, data.length,
                    sha256, stored.provider(), stored.bucket(), stored.objectKey(), stored.versionId(),
                    stored.etag(), stored.encryptionAlgorithm(), parsedDate, parsedAmount, AuthContext.userId());
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditLogService.log(companyId, "CONTRACT_ATTACHMENT", id,
                "UPLOAD", "上传" + categoryLabel(normalized) + " " + safeName);
        return list(contractId, normalized).stream()
                .filter(item -> id != null && id.equals(item.get("id")))
                .findFirst().orElseThrow(() -> new BusinessException("附件保存失败"));
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
        String fileType = PAYMENT_VOUCHER.equals(category) ? "payment-voucher" : "attachment";
        String key = keyPrefix() + "/file/" + companyId + "/" + contractId + "/"
                + fileType + "/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + UUID.randomUUID() + "-" + sha256 + "."
                + FileTypeInspector.extension(contentType);
        return objectStorageService.putImmutable(key, data, contentType, sha256);
    }

    private String keyPrefix() {
        String value = ossProperties == null ? "tradepass" : ossProperties.getKeyPrefix();
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
        if (!PAYMENT_VOUCHER.equals(normalized) && !OTHER.equals(normalized)) {
            throw new BusinessException("附件分类不正确");
        }
        return normalized;
    }

    private void validateContentType(String category, String contentType, String originalName) {
        if (PAYMENT_VOUCHER.equals(category)
                && !FileTypeInspector.isImage(contentType)
                && !"application/pdf".equals(contentType)) {
            throw new BusinessException("转款凭证仅支持图片或 PDF");
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

    private String categoryLabel(String category) {
        return PAYMENT_VOUCHER.equals(category) ? "转款凭证" : "其它资料";
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
