package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.BusinessDocument;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.BusinessDocumentMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalMemoServiceTest {
    private JdbcTemplate jdbc;
    private TradeContractMapper contractMapper;
    private BusinessDocumentMapper documentMapper;
    private AccessControlService accessControlService;
    private PersonalMemoService service;
    private TradeContract contract;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(TradeContract.class, BusinessDocument.class);
        jdbc = mock(JdbcTemplate.class);
        contractMapper = mock(TradeContractMapper.class);
        documentMapper = mock(BusinessDocumentMapper.class);
        accessControlService = mock(AccessControlService.class);
        service = new PersonalMemoService(jdbc, contractMapper, documentMapper,
                accessControlService, mock(AuditLogService.class));
        contract = new TradeContract();
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
    void getsAndSavesCurrentAccountsPrivateContractMemo() {
        Map<String, Object> row = Map.of("content", "跟进中", "updatedAt", LocalDateTime.now());
        doReturn(List.of(row)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(service.get(PersonalMemoService.CONTRACT, 12L)).containsEntry("content", "跟进中");
        assertThat(service.save(PersonalMemoService.CONTRACT, 12L, "  跟进中  "))
                .containsEntry("content", "跟进中");
        verify(accessControlService, atLeastOnce()).requireAnyPermission(3L,
                "contract_view", "contract_sign", "order_create", "sales_order_receive");
    }

    @Test
    void supportsSalesMemoAndReturnsEmptyMemoWhenMissing() {
        BusinessDocument document = new BusinessDocument();
        document.setId(21L);
        document.setContractId(12L);
        document.setDocumentType("SALES_ORDER");
        when(documentMapper.selectById(21L)).thenReturn(document);
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(service.get("sales_order", 21L))
                .containsEntry("content", "")
                .containsEntry("updatedAt", null);
    }

    @Test
    void rejectsInvalidTargetsAndOversizedMemo() {
        assertThatThrownBy(() -> service.save("CONTRACT", 12L, "x".repeat(4001)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("备忘录不能超过 4000 字");
        assertThatThrownBy(() -> service.get("UNKNOWN", 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("备忘录类型不正确");

        AuthContext.set(8L, 9L);
        assertThatThrownBy(() -> service.get("CONTRACT", 12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("合同不存在");

        AuthContext.set(7L, 3L);
        BusinessDocument wrong = new BusinessDocument();
        wrong.setDocumentType("OTHER");
        when(documentMapper.selectById(99L)).thenReturn(wrong);
        assertThatThrownBy(() -> service.get("SALES_ORDER", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("销售单不存在");
    }
}
