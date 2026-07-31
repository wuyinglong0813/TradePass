package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.mapper.CounterpartyRelationMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.tradepass.config.OssProperties;

@Service
public class ReconciliationStatementService {
    private final JdbcTemplate jdbc;
    private final CounterpartyRelationMapper relationMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectStorageService objectStorageService;
    private final OssProperties ossProperties;

    @Autowired
    public ReconciliationStatementService(JdbcTemplate jdbc,
                                          CounterpartyRelationMapper relationMapper,
                                          AccessControlService accessControlService,
                                          AuditLogService auditLogService,
                                          ObjectStorageService objectStorageService,
                                          OssProperties ossProperties) {
        this.jdbc = jdbc;
        this.relationMapper = relationMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.objectStorageService = objectStorageService;
        this.ossProperties = ossProperties;
    }

    ReconciliationStatementService(JdbcTemplate jdbc,
                                   CounterpartyRelationMapper relationMapper,
                                   AccessControlService accessControlService,
                                   AuditLogService auditLogService) {
        this(jdbc, relationMapper, accessControlService, auditLogService, null, null);
    }

    public List<Map<String, Object>> list(Long counterpartyCompanyId) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "reconciliation");
        if (counterpartyCompanyId == null) {
            return queryStatements(companyId, null);
        }
        requireRelation(companyId, counterpartyCompanyId);
        return queryStatements(companyId, counterpartyCompanyId);
    }

    @Transactional
    public Map<String, Object> upload(Long counterpartyCompanyId, String period, String remark,
                                      String originalName, byte[] data) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "reconciliation");
        requireRelation(companyId, counterpartyCompanyId);
        String normalizedPeriod = normalizePeriod(period);
        String contentType = FileTypeInspector.inspect(data);
        if (!FileTypeInspector.isXlsx(contentType)) {
            throw new BusinessException("客户对账单仅支持 XLSX 文件");
        }
        String safeName = FileTypeInspector.sanitizeFileName(originalName, contentType);
        String safeRemark = remark == null ? "" : remark.trim();
        if (safeRemark.length() > 500) throw new BusinessException("备注不能超过 500 字");
        String sha256 = FileTypeInspector.sha256(data);
        ObjectStorageService.StoredObject stored = store(companyId, counterpartyCompanyId,
                normalizedPeriod, contentType, data, sha256);
        if (stored == null) {
            jdbc.update("""
                    INSERT INTO reconciliation_statement
                    (issuer_company_id, counterparty_company_id, statement_period, original_name,
                     content_type, file_size, file_data, sha256, remark, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, companyId, counterpartyCompanyId, normalizedPeriod, safeName, contentType,
                    data.length, data, sha256, safeRemark, AuthContext.userId());
        } else {
            jdbc.update("""
                    INSERT INTO reconciliation_statement
                    (issuer_company_id, counterparty_company_id, statement_period, original_name,
                     content_type, file_size, file_data, sha256, storage_provider, storage_bucket,
                     object_key, object_version_id, etag, encryption_algorithm, remark, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, companyId, counterpartyCompanyId, normalizedPeriod, safeName, contentType,
                    data.length, sha256, stored.provider(), stored.bucket(), stored.objectKey(),
                    stored.versionId(), stored.etag(), stored.encryptionAlgorithm(), safeRemark,
                    AuthContext.userId());
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditLogService.log(companyId, "RECONCILIATION_STATEMENT", id,
                "UPLOAD", "上传 " + normalizedPeriod + " 客户对账单 " + safeName);
        return queryStatements(companyId, counterpartyCompanyId).stream()
                .filter(item -> id != null && id.equals(item.get("id")))
                .findFirst().orElseThrow(() -> new BusinessException("对账单保存失败"));
    }

    public FilePayload getFile(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "reconciliation");
        List<FilePayload> files = jdbc.query("""
                        SELECT id, issuer_company_id, counterparty_company_id,
                               original_name, content_type, file_size, file_data, sha256,
                               storage_bucket, object_key, object_version_id
                        FROM reconciliation_statement WHERE id = ?
                        """, (rs, rowNum) -> new FilePayload(
                        rs.getLong("id"), rs.getLong("issuer_company_id"),
                        rs.getLong("counterparty_company_id"), rs.getString("original_name"),
                        rs.getString("content_type"), rs.getBytes("file_data"),
                        rs.getString("storage_bucket"), rs.getString("object_key"),
                        rs.getString("object_version_id"), rs.getLong("file_size"),
                        rs.getString("sha256")), id);
        if (files.isEmpty()) throw new BusinessException("对账单不存在");
        FilePayload file = files.get(0);
        if (!Long.valueOf(companyId).equals(file.issuerCompanyId())
                && !Long.valueOf(companyId).equals(file.counterpartyCompanyId())) {
            throw new BusinessException("对账单不存在");
        }
        if (file.data() != null) return file;
        if (objectStorageService == null || !objectStorageService.isEnabled()
                || file.objectKey() == null) {
            throw new BusinessException("对账单内容暂不可用，请联系管理员");
        }
        byte[] data = objectStorageService.get(new ObjectStorageService.ObjectReference(
                file.storageBucket(), file.objectKey(), file.objectVersionId(),
                file.fileSize(), file.sha256()));
        return file.withData(data);
    }

    private ObjectStorageService.StoredObject store(long companyId, Long counterpartyCompanyId,
                                                     String period, String contentType,
                                                     byte[] data, String sha256) {
        if (objectStorageService == null || !objectStorageService.isEnabled()) return null;
        String key = keyPrefix() + "/file/" + companyId + "/reconciliation/"
                + counterpartyCompanyId + "/" + period + "/" + UUID.randomUUID() + "-"
                + sha256 + "." + FileTypeInspector.extension(contentType);
        return objectStorageService.putImmutable(key, data, contentType, sha256);
    }

    private String keyPrefix() {
        String value = ossProperties == null ? "tradepass" : ossProperties.getKeyPrefix();
        value = value == null ? "" : value.trim().replaceAll("^/+|/+$", "");
        return value.isBlank() ? "tradepass" : value;
    }

    private List<Map<String, Object>> queryStatements(long companyId, Long counterpartyCompanyId) {
        String counterpartFilter = counterpartyCompanyId == null ? "" : """
                AND ((statement.issuer_company_id = ? AND statement.counterparty_company_id = ?)
                  OR (statement.issuer_company_id = ? AND statement.counterparty_company_id = ?))
                """;
        String sql = """
                SELECT statement.id, statement.issuer_company_id, statement.counterparty_company_id,
                       statement.statement_period, statement.original_name, statement.content_type,
                       statement.file_size, statement.remark, statement.created_at,
                       issuer.name AS issuer_company_name,
                       CASE WHEN statement.issuer_company_id = ? THEN counterparty.name ELSE issuer.name END AS counterparty_name
                FROM reconciliation_statement statement
                JOIN company issuer ON issuer.id = statement.issuer_company_id
                JOIN company counterparty ON counterparty.id = statement.counterparty_company_id
                WHERE (statement.issuer_company_id = ? OR statement.counterparty_company_id = ?)
                """ + counterpartFilter + " ORDER BY statement.statement_period DESC, statement.created_at DESC, statement.id DESC";
        Object[] args = counterpartyCompanyId == null
                ? new Object[]{companyId, companyId, companyId}
                : new Object[]{companyId, companyId, companyId,
                companyId, counterpartyCompanyId, counterpartyCompanyId, companyId};
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", rs.getLong("id"));
            view.put("issuerCompanyId", rs.getLong("issuer_company_id"));
            view.put("counterpartyCompanyId", rs.getLong("counterparty_company_id"));
            view.put("issuerCompanyName", rs.getString("issuer_company_name"));
            view.put("counterpartyName", rs.getString("counterparty_name"));
            view.put("statementPeriod", rs.getString("statement_period"));
            view.put("originalName", rs.getString("original_name"));
            view.put("contentType", rs.getString("content_type"));
            view.put("fileSize", rs.getLong("file_size"));
            view.put("remark", rs.getString("remark"));
            view.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
            return view;
        }, args);
    }

    private void requireRelation(long companyId, Long counterpartyCompanyId) {
        if (counterpartyCompanyId == null || companyId == counterpartyCompanyId) {
            throw new BusinessException("请选择合作企业");
        }
        long count = relationMapper.countActiveBetween(companyId, counterpartyCompanyId);
        if (count == 0) throw new BusinessException("合作企业关系不存在");
    }

    private String normalizePeriod(String period) {
        String value = period == null ? "" : period.trim();
        if (!value.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new BusinessException("对账期间格式应为 YYYY-MM");
        }
        return value;
    }

    public record FilePayload(Long id, Long issuerCompanyId, Long counterpartyCompanyId,
                              String originalName, String contentType, byte[] data,
                              String storageBucket, String objectKey, String objectVersionId,
                              Long fileSize, String sha256) {
        public FilePayload(Long id, Long issuerCompanyId, Long counterpartyCompanyId,
                           String originalName, String contentType, byte[] data) {
            this(id, issuerCompanyId, counterpartyCompanyId, originalName, contentType, data,
                    null, null, null, data == null ? null : (long) data.length,
                    data == null ? null : FileTypeInspector.sha256(data));
        }

        FilePayload withData(byte[] value) {
            return new FilePayload(id, issuerCompanyId, counterpartyCompanyId,
                    originalName, contentType, value, storageBucket, objectKey,
                    objectVersionId, fileSize, sha256);
        }
    }
}
