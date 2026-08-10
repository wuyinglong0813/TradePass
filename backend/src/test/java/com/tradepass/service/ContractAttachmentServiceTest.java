package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.TradeContract;
import com.tradepass.config.StorageProperties;
import com.tradepass.mapper.TradeContractMapper;
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

class ContractAttachmentServiceTest {
    private JdbcTemplate jdbc;
    private TradeContractMapper contractMapper;
    private ContractAttachmentService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(TradeContract.class);
        jdbc = mock(JdbcTemplate.class);
        contractMapper = mock(TradeContractMapper.class);
        service = new ContractAttachmentService(jdbc, contractMapper,
                mock(AccessControlService.class), mock(AuditLogService.class));
        TradeContract contract = new TradeContract();
        contract.setId(12L);
        contract.setCompanyId(3L);
        contract.setCounterpartyCompanyId(4L);
        when(contractMapper.selectById(12L)).thenReturn(contract);
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void uploadsPaymentVoucherAndReturnsSharedMetadata() {
        Map<String, Object> row = Map.of(
                "id", 8L,
                "originalName", "转款凭证.pdf",
                "contentType", "application/pdf",
                "fileSize", 9L
        );
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(8L);
        doReturn(List.of(row)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        Map<String, Object> uploaded = service.upload(12L, "payment_voucher", "../转款凭证.pdf",
                "%PDF-1.7".getBytes(), "2026-07-28", "100.126");
        assertThat(uploaded).containsEntry("id", 8L);
        assertThat(service.list(12L, "PAYMENT_VOUCHER")).containsExactly(row);
    }

    @Test
    void acceptsWordOnlyForOtherAttachments() throws Exception {
        byte[] docx = ooxml("word/document.xml");
        Map<String, Object> row = Map.of("id", 9L, "originalName", "说明.docx");
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(9L);
        doReturn(List.of(row)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(service.upload(12L, "OTHER", "说明.docx", docx, null, null))
                .containsEntry("id", 9L);
        assertThatThrownBy(() -> service.upload(12L, "PAYMENT_VOUCHER", "说明.docx",
                docx, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("转款凭证仅支持图片或 PDF");
    }

    @Test
    void acceptsOnlyImagesOrPdfForInvoices() throws Exception {
        byte[] pdf = "%PDF-1.7".getBytes();
        byte[] docx = ooxml("word/document.xml");
        Map<String, Object> row = Map.of("id", 10L, "originalName", "发票.pdf");
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(10L);
        doReturn(List.of(row)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(service.upload(12L, "INVOICE", "发票.pdf", pdf, null, null,
                "FP-2026-001", "2026-07-28", "88.50"))
                .containsEntry("id", 10L);
        assertThatThrownBy(() -> service.upload(12L, "INVOICE", "发票.docx",
                docx, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("发票仅支持图片或 PDF");
    }

    @Test
    void letsEitherContractPartyReadAndRejectsInvalidInput() {
        byte[] data = "%PDF-1.7".getBytes();
        ContractAttachmentService.FilePayload payload = new ContractAttachmentService.FilePayload(
                8L, 12L, "凭证.pdf", "application/pdf", data);
        doReturn(List.of(payload)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        AuthContext.set(8L, 4L);
        assertThat(service.getFile(8L).data()).containsExactly(data);

        AuthContext.set(7L, 3L);
        assertThatThrownBy(() -> service.list(12L, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("附件分类不正确");
        assertThatThrownBy(() -> service.upload(12L, "PAYMENT_VOUCHER", "凭证.pdf",
                data, "bad-date", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("转款日期格式不正确");
        assertThatThrownBy(() -> service.upload(12L, "PAYMENT_VOUCHER", "凭证.pdf",
                data, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入转款金额");
        assertThatThrownBy(() -> service.upload(12L, "PAYMENT_VOUCHER", "凭证.pdf",
                data, null, "-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("转款金额格式不正确");

        AuthContext.set(7L, 99L);
        assertThatThrownBy(() -> service.list(12L, "OTHER"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("合同不存在");
    }

    @Test
    void reportsMissingAttachment() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        assertThatThrownBy(() -> service.getFile(88L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("附件不存在");
    }

    @Test
    void storesNewAttachmentInCloudStorageAndReadsItBackWithRecordedVersion() {
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(storage.isEnabled()).thenReturn(true);
        byte[] data = "%PDF-1.7".getBytes();
        String sha256 = FileTypeInspector.sha256(data);
        when(storage.putImmutable(anyString(), any(byte[].class), anyString(), anyString()))
                .thenReturn(new ObjectStorageService.StoredObject(
                        "CLOUDBASE_COS", "bucket", "tradepass/file/3/12/attachment/file.pdf",
                        "v1", "etag", "CLOUDBASE_MANAGED", data.length, sha256));
        StorageProperties properties = new StorageProperties();
        properties.setKeyPrefix("tradepass");
        ContractAttachmentService ossService = new ContractAttachmentService(
                jdbc, contractMapper, mock(AccessControlService.class), mock(AuditLogService.class),
                storage, properties);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(8L);
        doReturn(List.of(Map.of("id", 8L))).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(ossService.upload(12L, "OTHER", "资料.pdf", data, null, null))
                .containsEntry("id", 8L);
        verify(storage).putImmutable(argThat(key -> key.startsWith(
                        "tradepass/file/3/12/attachment/")), any(byte[].class),
                eq("application/pdf"), eq(sha256));

        ContractAttachmentService.FilePayload reference = new ContractAttachmentService.FilePayload(
                8L, 12L, "资料.pdf", "application/pdf", null,
                "bucket", "tradepass/file/3/12/attachment/file.pdf", "v1",
                (long) data.length, sha256);
        doReturn(List.of(reference)).when(jdbc)
                .query(anyString(), any(RowMapper.class), any(Object[].class));
        when(storage.get(any(ObjectStorageService.ObjectReference.class))).thenReturn(data);
        assertThat(ossService.getFile(8L).data()).containsExactly(data);
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
