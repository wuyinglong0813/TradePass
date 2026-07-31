package com.tradepass.service;

import com.tradepass.config.OssProperties;
import com.tradepass.dto.response.ContractPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class LegacyBlobMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyBlobMigrationRunner.class);
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final ObjectStorageService objectStorageService;
    private final ContractArchiveService contractArchiveService;
    private final OssProperties properties;

    public LegacyBlobMigrationRunner(JdbcTemplate jdbc,
                                     ObjectStorageService objectStorageService,
                                     ContractArchiveService contractArchiveService,
                                     OssProperties properties) {
        this.jdbc = jdbc;
        this.objectStorageService = objectStorageService;
        this.contractArchiveService = contractArchiveService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!objectStorageService.isEnabled() || !properties.isMigrateLegacyBlobs()) return;
        int failures = 0;
        failures += migrateAttachments();
        failures += migrateLogisticsDocuments();
        failures += migrateStatements();
        failures += archiveActiveContracts();
        if (failures == 0) {
            log.info("历史合同及文件已完成 OSS 加密归档迁移");
        } else {
            log.warn("历史 OSS 迁移有 {} 条失败；原 MySQL BLOB 未删除，可修复后安全重试", failures);
        }
    }

    private int migrateAttachments() {
        return migrateBatches("contract_attachment", "file_data", (row, data, sha256) -> {
            String key = keyPrefix() + "/file/legacy/contract-attachments/" + row.id() + "/"
                    + sha256 + "." + FileTypeInspector.extension(row.contentType());
            ObjectStorageService.StoredObject stored = objectStorageService.putImmutable(
                    key, data, row.contentType(), sha256);
            jdbc.update("""
                    UPDATE contract_attachment
                    SET storage_provider = ?, storage_bucket = ?, object_key = ?, object_version_id = ?,
                        etag = ?, encryption_algorithm = ?, file_data = NULL
                    WHERE id = ? AND object_key IS NULL AND file_data IS NOT NULL
                    """, stored.provider(), stored.bucket(), stored.objectKey(), stored.versionId(),
                    stored.etag(), stored.encryptionAlgorithm(), row.id());
        });
    }

    private int migrateLogisticsDocuments() {
        return migrateBatches("logistics_document", "image_data", (row, data, sha256) -> {
            String key = keyPrefix() + "/file/legacy/logistics/" + row.id() + "/"
                    + sha256 + "." + FileTypeInspector.extension(row.contentType());
            ObjectStorageService.StoredObject stored = objectStorageService.putImmutable(
                    key, data, row.contentType(), sha256);
            jdbc.update("""
                    UPDATE logistics_document
                    SET sha256 = ?, storage_provider = ?, storage_bucket = ?, object_key = ?,
                        object_version_id = ?, etag = ?, encryption_algorithm = ?, image_data = NULL
                    WHERE id = ? AND object_key IS NULL AND image_data IS NOT NULL
                    """, sha256, stored.provider(), stored.bucket(), stored.objectKey(),
                    stored.versionId(), stored.etag(), stored.encryptionAlgorithm(), row.id());
        });
    }

    private int migrateStatements() {
        return migrateBatches("reconciliation_statement", "file_data", (row, data, sha256) -> {
            String key = keyPrefix() + "/file/legacy/reconciliation/" + row.id() + "/"
                    + sha256 + "." + FileTypeInspector.extension(row.contentType());
            ObjectStorageService.StoredObject stored = objectStorageService.putImmutable(
                    key, data, row.contentType(), sha256);
            jdbc.update("""
                    UPDATE reconciliation_statement
                    SET storage_provider = ?, storage_bucket = ?, object_key = ?, object_version_id = ?,
                        etag = ?, encryption_algorithm = ?, file_data = NULL
                    WHERE id = ? AND object_key IS NULL AND file_data IS NOT NULL
                    """, stored.provider(), stored.bucket(), stored.objectKey(), stored.versionId(),
                    stored.etag(), stored.encryptionAlgorithm(), row.id());
        });
    }

    private int migrateBatches(String table, String blobColumn, BlobMigrator migrator) {
        long cursor = 0;
        int failures = 0;
        while (true) {
            String sql = "SELECT id, content_type, " + blobColumn + " AS file_data, sha256 FROM "
                    + table + " WHERE id > ? AND object_key IS NULL AND " + blobColumn
                    + " IS NOT NULL ORDER BY id LIMIT " + BATCH_SIZE;
            List<LegacyBlob> rows = jdbc.query(sql, (rs, rowNum) -> new LegacyBlob(
                    rs.getLong("id"), rs.getString("content_type"), rs.getBytes("file_data"),
                    rs.getString("sha256")), cursor);
            if (rows.isEmpty()) return failures;
            for (LegacyBlob row : rows) {
                cursor = row.id();
                try {
                    String sha256 = row.sha256() == null || row.sha256().isBlank()
                            ? FileTypeInspector.sha256(row.data()) : row.sha256();
                    migrator.migrate(row, row.data(), sha256);
                } catch (RuntimeException exception) {
                    failures++;
                    log.error("历史文件迁移失败：table={}, id={}", table, row.id(), exception);
                }
            }
        }
    }

    private int archiveActiveContracts() {
        long cursor = 0;
        int failures = 0;
        while (true) {
            List<HistoricalContract> rows = jdbc.query("""
                            SELECT contract.*
                            FROM trade_contract contract
                            LEFT JOIN contract_archive archive
                              ON archive.contract_id = contract.id AND archive.version_no = contract.version_no
                            WHERE contract.id > ? AND contract.status = 'ACTIVE' AND archive.id IS NULL
                            ORDER BY contract.id
                            LIMIT 50
                            """, (rs, rowNum) -> mapContract(rs), cursor);
            if (rows.isEmpty()) return failures;
            for (HistoricalContract row : rows) {
                cursor = row.id();
                try {
                    contractArchiveService.archiveOnApproval(row.payload(), row.archivedBy());
                } catch (RuntimeException exception) {
                    failures++;
                    log.error("历史生效合同 PDF 归档失败：contractId={}", row.id(), exception);
                }
            }
        }
    }

    private HistoricalContract mapContract(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long companyId = rs.getLong("company_id");
        long counterpartyId = rs.getLong("counterparty_company_id");
        long approvedBy = rs.getLong("approved_by");
        if (rs.wasNull()) approvedBy = rs.getLong("initiated_by");
        ContractPayload payload = new ContractPayload(
                String.valueOf(id), rs.getString("contract_no"), String.valueOf(companyId),
                String.valueOf(counterpartyId), rs.getString("counterparty_name"),
                rs.getString("direction"), rs.getString("name"), rs.getString("template_name"),
                rs.getBigDecimal("amount"), string(rs.getDate("start_date")),
                string(rs.getDate("end_date")), rs.getString("terms"), rs.getString("status"),
                rs.getInt("version_no"), String.valueOf(rs.getLong("initiated_by")),
                nullableLong(rs, "approved_by"), string(rs.getTimestamp("approved_at")),
                string(rs.getTimestamp("created_at")), String.valueOf(companyId),
                String.valueOf(counterpartyId), rs.getString("counterparty_name"),
                rs.getString("direction"), "OUTGOING");
        return new HistoricalContract(id, approvedBy, payload);
    }

    private String nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : String.valueOf(value);
    }

    private String string(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        return value.toString();
    }

    private String keyPrefix() {
        String value = properties.getKeyPrefix();
        value = value == null ? "" : value.trim().replaceAll("^/+|/+$", "");
        return value.isBlank() ? "tradepass" : value;
    }

    @FunctionalInterface
    private interface BlobMigrator {
        void migrate(LegacyBlob row, byte[] data, String sha256);
    }

    private record LegacyBlob(long id, String contentType, byte[] data, String sha256) {
    }

    private record HistoricalContract(long id, long archivedBy, ContractPayload payload) {
    }
}
