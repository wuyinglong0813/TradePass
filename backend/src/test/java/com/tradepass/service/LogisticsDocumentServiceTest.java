package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.LogisticsDocument;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.LogisticsDocumentMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsDocumentServiceTest {
    private LogisticsDocumentMapper documentMapper;
    private TradeContractMapper contractMapper;
    private AccessControlService accessControlService;
    private AuditLogService auditLogService;
    private LogisticsDocumentService service;
    private TradeContract contract;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(LogisticsDocument.class, TradeContract.class);
        documentMapper = mock(LogisticsDocumentMapper.class);
        contractMapper = mock(TradeContractMapper.class);
        accessControlService = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        service = new LogisticsDocumentService(
                documentMapper, contractMapper, accessControlService, auditLogService);

        contract = new TradeContract();
        contract.setId(44L);
        contract.setCompanyId(3L);
        contract.setCounterpartyCompanyId(4L);
        when(contractMapper.selectById(44L)).thenReturn(contract);
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void uploadsAndListsRealImageMetadataWithoutReturningBlob() {
        AtomicReference<LogisticsDocument> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            LogisticsDocument document = invocation.getArgument(0);
            document.setId(9L);
            inserted.set(document);
            return 1;
        }).when(documentMapper).insert(any(LogisticsDocument.class));
        when(documentMapper.selectById(9L)).thenAnswer(invocation -> inserted.get());
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};

        Map<String, Object> created = service.upload(44L, "../物流单.jpg", jpeg);

        assertThat(created).containsEntry("id", 9L)
                .containsEntry("contentType", "image/jpeg")
                .containsEntry("fileSize", 4L)
                .containsEntry("originalName", ".._物流单.jpg");
        assertThat(inserted.get().getImageData()).containsExactly(jpeg);
        verify(accessControlService)
                .requireAnyPermission(3L, "contract_sign", "order_create");

        when(documentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(inserted.get()));
        List<Map<String, Object>> listed = service.listDocuments(44L);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0)).doesNotContainKey("imageData");
    }

    @Test
    void rejectsPayloadThatIsNotAnImage() {
        assertThatThrownBy(() -> service.upload(44L, "物流单.txt", "not-image".getBytes()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支持 JPG、PNG、GIF 或 WebP 图片");
    }

    @Test
    void letsEitherContractPartyReadTheUploadedImage() {
        LogisticsDocument document = new LogisticsDocument();
        document.setId(9L);
        document.setCompanyId(3L);
        document.setContractId(44L);
        document.setOriginalName("物流单.png");
        document.setContentType("image/png");
        document.setImageData(new byte[]{1, 2, 3});
        when(documentMapper.selectById(9L)).thenReturn(document);
        AuthContext.set(8L, 4L);

        assertThat(service.getImage(9L)).isSameAs(document);
        verify(accessControlService)
                .requireAnyPermission(4L, "contract_view", "contract_sign");
    }
}
