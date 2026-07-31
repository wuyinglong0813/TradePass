package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.CounterpartyRelationEntity;
import com.tradepass.config.StorageProperties;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ReconciliationStatementServiceTest {
    private JdbcTemplate jdbc;
    private CounterpartyRelationMapper relationMapper;
    private ReconciliationStatementService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(CounterpartyRelationEntity.class);
        jdbc = mock(JdbcTemplate.class);
        relationMapper = mock(CounterpartyRelationMapper.class);
        service = new ReconciliationStatementService(jdbc, relationMapper,
                mock(AccessControlService.class), mock(AuditLogService.class));
        when(relationMapper.countActiveBetween(3L, 4L)).thenReturn(1L);
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void uploadsAndListsXlsxForActiveCounterparty() throws Exception {
        Map<String, Object> row = Map.of(
                "id", 18L,
                "statementPeriod", "2026-07",
                "originalName", "对账单.xlsx"
        );
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(18L);
        doReturn(List.of(row)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        Map<String, Object> uploaded = service.upload(4L, "2026-07", " 七月账单 ",
                "../对账单.xlsx", ooxml("xl/workbook.xml"));
        assertThat(uploaded).containsEntry("id", 18L);
        assertThat(service.list(4L)).containsExactly(row);
        assertThat(service.list(null)).containsExactly(row);
    }

    @Test
    void letsEitherPartyDownloadStatement() {
        byte[] data = new byte[]{1, 2, 3};
        ReconciliationStatementService.FilePayload payload =
                new ReconciliationStatementService.FilePayload(18L, 3L, 4L,
                        "对账单.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);
        doReturn(List.of(payload)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        AuthContext.set(8L, 4L);
        assertThat(service.getFile(18L).data()).containsExactly(data);
        AuthContext.set(9L, 5L);
        assertThatThrownBy(() -> service.getFile(18L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("对账单不存在");
    }

    @Test
    void rejectsBadRelationPeriodTypeAndMissingFile() throws Exception {
        byte[] xlsx = ooxml("xl/workbook.xml");
        assertThatThrownBy(() -> service.upload(3L, "2026-07", "", "self.xlsx", xlsx))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请选择合作企业");
        assertThatThrownBy(() -> service.upload(4L, "2026-13", "", "bad.xlsx", xlsx))
                .isInstanceOf(BusinessException.class)
                .hasMessage("对账期间格式应为 YYYY-MM");
        assertThatThrownBy(() -> service.upload(4L, "2026-07", "", "bad.pdf", "%PDF-1.7".getBytes()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户对账单仅支持 XLSX 文件");
        assertThatThrownBy(() -> service.upload(4L, "2026-07", "x".repeat(501), "bad.xlsx", xlsx))
                .isInstanceOf(BusinessException.class)
                .hasMessage("备注不能超过 500 字");

        when(relationMapper.countActiveBetween(3L, 4L)).thenReturn(0L);
        assertThatThrownBy(() -> service.list(4L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("合作企业关系不存在");

        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        assertThatThrownBy(() -> service.getFile(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("对账单不存在");
    }

    @Test
    void storesAndReadsStatementThroughCloudStorage() throws Exception {
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(storage.isEnabled()).thenReturn(true);
        byte[] data = ooxml("xl/workbook.xml");
        String sha256 = FileTypeInspector.sha256(data);
        when(storage.putImmutable(anyString(), any(byte[].class), anyString(), anyString()))
                .thenReturn(new ObjectStorageService.StoredObject(
                        "CLOUDBASE_COS", "bucket", "tradepass/file/3/reconciliation/4/2026-07/file.xlsx",
                        "v1", "etag", "CLOUDBASE_MANAGED", data.length, sha256));
        ReconciliationStatementService ossService = new ReconciliationStatementService(
                jdbc, relationMapper, mock(AccessControlService.class), mock(AuditLogService.class),
                storage, new StorageProperties());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(18L);
        doReturn(List.of(Map.of("id", 18L))).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(ossService.upload(4L, "2026-07", "", "账单.xlsx", data))
                .containsEntry("id", 18L);
        verify(storage).putImmutable(argThat(key -> key.startsWith(
                        "tradepass/file/3/reconciliation/4/2026-07/")), any(byte[].class),
                eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), eq(sha256));

        ReconciliationStatementService.FilePayload reference =
                new ReconciliationStatementService.FilePayload(18L, 3L, 4L, "账单.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null,
                        "bucket", "tradepass/file/3/reconciliation/4/2026-07/file.xlsx", "v1",
                        (long) data.length, sha256);
        doReturn(List.of(reference)).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(Object[].class));
        when(storage.get(any(ObjectStorageService.ObjectReference.class))).thenReturn(data);
        assertThat(ossService.getFile(18L).data()).containsExactly(data);
    }

    private byte[] ooxml(String partName) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(partName));
            zip.write("<root/>".getBytes());
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
