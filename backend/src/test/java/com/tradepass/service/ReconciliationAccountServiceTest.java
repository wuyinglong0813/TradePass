package com.tradepass.service;

import com.tradepass.entity.BusinessDocument;
import com.tradepass.entity.CounterpartyRelationEntity;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.support.MybatisTestSupport;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReconciliationAccountServiceTest {
    private JdbcTemplate jdbc;
    private ReconciliationAccountService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(CounterpartyRelationEntity.class);
        jdbc = mock(JdbcTemplate.class);
        service = new ReconciliationAccountService(jdbc, mock(CounterpartyRelationMapper.class),
                mock(AccessControlService.class));
    }

    @Test
    void writesEveryApprovedSourceWithCanonicalCompanyPairAndIdempotentKey() {
        BusinessDocument salesOrder = new BusinessDocument();
        salesOrder.setId(31L);
        salesOrder.setCompanyId(9L);
        salesOrder.setRecipientCompanyId(3L);
        salesOrder.setContractId(12L);
        salesOrder.setDocumentNo("XS-31");
        service.recordSalesOrder(salesOrder, new BigDecimal("88.50"),
                LocalDate.of(2026, 8, 6), 7L, LocalDateTime.of(2026, 8, 6, 10, 0));

        TradeContract purchaseContract = new TradeContract();
        purchaseContract.setId(12L);
        purchaseContract.setCompanyId(3L);
        purchaseContract.setCounterpartyCompanyId(9L);
        purchaseContract.setDirection("PURCHASE");
        service.recordAttachment(purchaseContract, ReconciliationAccountService.INVOICE, 41L,
                LocalDate.of(2026, 8, 6), "FP-41", new BigDecimal("88.50"),
                9L, 8L, LocalDateTime.of(2026, 8, 6, 11, 0));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(contains("ON DUPLICATE KEY UPDATE"), args.capture());
        assertThat(args.getAllValues().get(0)[0]).isEqualTo(3L);
        assertThat(args.getAllValues().get(0)[1]).isEqualTo(9L);
        assertThat(args.getAllValues().get(1)[8]).isEqualTo(9L);
        assertThat(args.getAllValues().get(1)[9]).isEqualTo(3L);
    }

    @Test
    void fillsTheProvidedWorkbookTemplateWithApprovedDocuments() throws Exception {
        List<ReconciliationAccountService.ContractAccount> contracts = List.of(
                new ReconciliationAccountService.ContractAccount(12L, "HT-001",
                        new BigDecimal("1000.00"), LocalDate.of(2026, 8, 6)));
        Map<Long, List<ReconciliationAccountService.WorkbookEntry>> entries = Map.of(12L, List.of(
                new ReconciliationAccountService.WorkbookEntry(ReconciliationAccountService.SALES_ORDER,
                        LocalDate.of(2026, 8, 7), new BigDecimal("500.00")),
                new ReconciliationAccountService.WorkbookEntry(ReconciliationAccountService.SALES_ORDER,
                        LocalDate.of(2026, 8, 8), new BigDecimal("200.00")),
                new ReconciliationAccountService.WorkbookEntry(ReconciliationAccountService.PAYMENT_VOUCHER,
                        LocalDate.of(2026, 8, 9), new BigDecimal("300.00")),
                new ReconciliationAccountService.WorkbookEntry(ReconciliationAccountService.INVOICE,
                        LocalDate.of(2026, 8, 10), new BigDecimal("100.00"))));

        byte[] data = service.generateWorkbook("甲公司与乙公司对账单", contracts, entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("甲公司与乙公司对账单");
            assertThat(sheet.getMergedRegion(0).formatAsString()).isEqualTo("C4:J4");
            assertThat(sheet.getRow(5).getCell(3).getStringCellValue()).isEqualTo("HT-001");
            assertThat(sheet.getRow(5).getCell(4).getNumericCellValue()).isEqualTo(1000.00);
            assertThat(sheet.getRow(5).getCell(5).getNumericCellValue()).isEqualTo(500.00);
            assertThat(sheet.getRow(6).getCell(5).getNumericCellValue()).isEqualTo(200.00);
            assertThat(sheet.getRow(5).getCell(6).getNumericCellValue()).isEqualTo(300.00);
            assertThat(sheet.getRow(5).getCell(7).getCellFormula()).isEqualTo("SUM(F6:F7)-SUM(G6:G7)");
            assertThat(sheet.getRow(5).getCell(7).getNumericCellValue()).isEqualTo(400.00);
            assertThat(sheet.getRow(5).getCell(8).getNumericCellValue()).isEqualTo(100.00);
            assertThat(sheet.getRow(5).getCell(9).getNumericCellValue()).isEqualTo(600.00);
            assertThat(sheet.getRow(19).getCell(4).getCellFormula()).isEqualTo("SUM(E6:E19)");
            assertThat(sheet.getRow(19).getCell(4).getNumericCellValue()).isEqualTo(1000.00);
        }
    }

    @Test
    void extendsTheTemplateWhenApprovedDocumentsExceedTheOriginalRows() throws Exception {
        List<ReconciliationAccountService.WorkbookEntry> sales = java.util.stream.IntStream.range(0, 15)
                .mapToObj(index -> new ReconciliationAccountService.WorkbookEntry(
                        ReconciliationAccountService.SALES_ORDER,
                        LocalDate.of(2026, 8, 1).plusDays(index), new BigDecimal("10.00")))
                .toList();
        byte[] data = service.generateWorkbook("甲公司与乙公司对账单", List.of(
                new ReconciliationAccountService.ContractAccount(12L, "HT-001",
                        new BigDecimal("1000.00"), LocalDate.of(2026, 8, 1))), Map.of(12L, sales));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(19).getCell(5).getNumericCellValue()).isEqualTo(10.00);
            assertThat(sheet.getRow(20).getCell(2).getStringCellValue()).isEqualTo("合计");
            assertThat(sheet.getRow(20).getCell(5).getCellFormula()).isEqualTo("SUM(F6:F20)");
            assertThat(sheet.getRow(20).getCell(5).getNumericCellValue()).isEqualTo(150.00);
            assertThat(sheet.getRow(20).getCell(9).getNumericCellValue()).isEqualTo(150.00);
            assertThat(sheet.getRow(19).getCell(5).getCellStyle().getIndex())
                    .isEqualTo(sheet.getRow(18).getCell(5).getCellStyle().getIndex());
        }
    }

    @Test
    void appliesTheTemplateDateFormatToEveryContractStartRow() throws Exception {
        List<ReconciliationAccountService.ContractAccount> contracts = List.of(
                new ReconciliationAccountService.ContractAccount(12L, "HT-001",
                        new BigDecimal("1000.00"), LocalDate.of(2026, 8, 1)),
                new ReconciliationAccountService.ContractAccount(13L, "HT-002",
                        new BigDecimal("2000.00"), LocalDate.of(2026, 8, 3)));
        Map<Long, List<ReconciliationAccountService.WorkbookEntry>> entries = Map.of(
                12L, List.of(
                        new ReconciliationAccountService.WorkbookEntry(ReconciliationAccountService.SALES_ORDER,
                                LocalDate.of(2026, 8, 1), new BigDecimal("10.00")),
                        new ReconciliationAccountService.WorkbookEntry(ReconciliationAccountService.SALES_ORDER,
                                LocalDate.of(2026, 8, 2), new BigDecimal("10.00"))),
                13L, List.of(new ReconciliationAccountService.WorkbookEntry(
                        ReconciliationAccountService.SALES_ORDER,
                        LocalDate.of(2026, 8, 3), new BigDecimal("20.00"))));

        byte[] data = service.generateWorkbook("甲公司与乙公司对账单", contracts, entries);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            var sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(java.util.Locale.US);
            assertThat(formatter.formatCellValue(sheet.getRow(5).getCell(2))).isEqualTo("8/1/26");
            assertThat(formatter.formatCellValue(sheet.getRow(7).getCell(2))).isEqualTo("8/3/26");
            assertThat(sheet.getRow(7).getCell(2).getCellStyle().getIndex())
                    .isEqualTo(sheet.getRow(5).getCell(2).getCellStyle().getIndex());
        }
    }
}
