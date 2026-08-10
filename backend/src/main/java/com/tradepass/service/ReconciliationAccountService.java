package com.tradepass.service;

import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.BusinessDocument;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.CounterpartyRelationMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationAccountService {
    public static final String SALES_ORDER = "SALES_ORDER";
    public static final String PAYMENT_VOUCHER = "PAYMENT_VOUCHER";
    public static final String INVOICE = "INVOICE";
    private static final String WORKBOOK_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String WORKBOOK_TEMPLATE = "reconciliation/对账单.xlsx";
    private static final int DATA_START_ROW = 5;
    private static final int TEMPLATE_DATA_ROWS = 14;
    private static final int TEMPLATE_TOTAL_ROW = 19;

    private final JdbcTemplate jdbc;
    private final CounterpartyRelationMapper relationMapper;
    private final AccessControlService accessControlService;

    public ReconciliationAccountService(JdbcTemplate jdbc,
                                        CounterpartyRelationMapper relationMapper,
                                        AccessControlService accessControlService) {
        this.jdbc = jdbc;
        this.relationMapper = relationMapper;
        this.accessControlService = accessControlService;
    }

    public List<Map<String, Object>> listAccounts() {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "reconciliation");
        List<Map<String, Object>> counterparties = jdbc.query("""
                        SELECT DISTINCT pair.counterparty_id, company.name AS counterparty_name
                        FROM (
                            SELECT CASE WHEN relation.company_id = ?
                                        THEN relation.counterparty_company_id ELSE relation.company_id END AS counterparty_id
                            FROM counterparty_relation relation
                            WHERE relation.status = 'ACTIVE'
                              AND (relation.company_id = ? OR relation.counterparty_company_id = ?)
                              AND relation.counterparty_company_id IS NOT NULL
                        ) pair
                        JOIN company ON company.id = pair.counterparty_id
                        ORDER BY company.name, pair.counterparty_id
                        """, (rs, rowNum) -> Map.<String, Object>of(
                        "counterpartyCompanyId", rs.getLong("counterparty_id"),
                        "counterpartyName", rs.getString("counterparty_name")),
                companyId, companyId, companyId);
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Map<String, Object> counterparty : counterparties) {
            long counterpartyId = ((Number) counterparty.get("counterpartyCompanyId")).longValue();
            accounts.add(account(companyId, counterpartyId,
                    String.valueOf(counterparty.get("counterpartyName")), false));
        }
        return accounts;
    }

    public Map<String, Object> account(Long counterpartyCompanyId) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "reconciliation");
        requireRelation(companyId, counterpartyCompanyId);
        String name = jdbc.queryForObject("SELECT name FROM company WHERE id = ?", String.class,
                counterpartyCompanyId);
        return account(companyId, counterpartyCompanyId, name, true);
    }

    public WorkbookPayload workbook(Long counterpartyCompanyId) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "reconciliation");
        requireRelation(companyId, counterpartyCompanyId);

        long companyAId = Math.min(companyId, counterpartyCompanyId);
        long companyBId = Math.max(companyId, counterpartyCompanyId);
        List<String> companyNames = jdbc.query("""
                        SELECT name FROM company WHERE id IN (?, ?) ORDER BY id
                        """, (rs, rowNum) -> rs.getString("name"), companyAId, companyBId);
        if (companyNames.size() != 2) throw new BusinessException("对账企业信息不完整");

        List<ContractAccount> contracts = jdbc.query("""
                        SELECT contract.id, contract.contract_no, contract.amount,
                               COALESCE(contract.start_date, DATE(contract.approved_at),
                                        DATE(contract.created_at)) AS contract_date
                        FROM trade_contract contract
                        WHERE ((contract.company_id = ? AND contract.counterparty_company_id = ?)
                                OR (contract.company_id = ? AND contract.counterparty_company_id = ?))
                          AND EXISTS (
                              SELECT 1 FROM reconciliation_entry entry
                              WHERE entry.contract_id = contract.id
                                AND entry.company_a_id = ? AND entry.company_b_id = ?
                          )
                        ORDER BY contract_date, contract.id
                        """, (rs, rowNum) -> new ContractAccount(
                        rs.getLong("id"), rs.getString("contract_no"),
                        rs.getBigDecimal("amount"), rs.getObject("contract_date", LocalDate.class)),
                companyId, counterpartyCompanyId, counterpartyCompanyId, companyId,
                companyAId, companyBId);

        Map<Long, List<WorkbookEntry>> entriesByContract = new HashMap<>();
        jdbc.query("""
                        SELECT contract_id, source_type, business_date, amount
                        FROM reconciliation_entry
                        WHERE company_a_id = ? AND company_b_id = ?
                        ORDER BY contract_id, business_date, approved_at, id
                        """, rs -> {
                    entriesByContract.computeIfAbsent(rs.getLong("contract_id"),
                            ignored -> new ArrayList<>()).add(new WorkbookEntry(
                            rs.getString("source_type"),
                            rs.getObject("business_date", LocalDate.class),
                            rs.getBigDecimal("amount")));
                }, companyAId, companyBId);

        String title = companyNames.get(0) + "与" + companyNames.get(1) + "对账单";
        byte[] data = generateWorkbook(title, contracts, entriesByContract);
        return new WorkbookPayload(safeFileName(title) + ".xlsx", WORKBOOK_CONTENT_TYPE, data);
    }

    public void recordSalesOrder(BusinessDocument document, BigDecimal amount,
                                 LocalDate businessDate, long approvedBy,
                                 LocalDateTime approvedAt) {
        if (document == null || document.getId() == null || document.getRecipientCompanyId() == null) {
            throw new BusinessException("销售单对账信息不完整");
        }
        insertEntry(document.getCompanyId(), document.getRecipientCompanyId(),
                document.getContractId(), SALES_ORDER, document.getId(), businessDate,
                document.getDocumentNo(), amount, document.getCompanyId(),
                document.getRecipientCompanyId(), document.getCompanyId(), approvedBy, approvedAt);
    }

    public void recordAttachment(TradeContract contract, String sourceType, long sourceId,
                                 LocalDate businessDate, String documentNo, BigDecimal amount,
                                 long issuerCompanyId, long approvedBy, LocalDateTime approvedAt) {
        if (contract == null || contract.getCounterpartyCompanyId() == null) {
            throw new BusinessException("附件对账合同信息不完整");
        }
        long supplierCompanyId = supplierCompanyId(contract);
        long buyerCompanyId = buyerCompanyId(contract);
        insertEntry(supplierCompanyId, buyerCompanyId, contract.getId(), sourceType, sourceId,
                businessDate, documentNo, amount, supplierCompanyId, buyerCompanyId,
                issuerCompanyId, approvedBy, approvedAt);
    }

    private void insertEntry(long leftCompanyId, long rightCompanyId, Long contractId,
                             String sourceType, Long sourceId, LocalDate businessDate,
                             String documentNo, BigDecimal amount, long supplierCompanyId,
                             long buyerCompanyId, long issuerCompanyId, long approvedBy,
                             LocalDateTime approvedAt) {
        if (contractId == null || sourceId == null || businessDate == null
                || amount == null || amount.signum() < 0) {
            throw new BusinessException("单据对账金额或日期不完整");
        }
        long companyAId = Math.min(leftCompanyId, rightCompanyId);
        long companyBId = Math.max(leftCompanyId, rightCompanyId);
        jdbc.update("""
                INSERT INTO reconciliation_entry
                (company_a_id, company_b_id, contract_id, source_type, source_id,
                 business_date, document_no, amount, supplier_company_id, buyer_company_id,
                 issuer_company_id, approved_by, approved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE source_id = VALUES(source_id)
                """, companyAId, companyBId, contractId, sourceType, sourceId,
                businessDate, safe(documentNo), money(amount), supplierCompanyId, buyerCompanyId,
                issuerCompanyId, approvedBy, approvedAt == null ? LocalDateTime.now() : approvedAt);
    }

    private Map<String, Object> account(long companyId, long counterpartyCompanyId,
                                        String counterpartyName, boolean includeEntries) {
        long companyAId = Math.min(companyId, counterpartyCompanyId);
        long companyBId = Math.max(companyId, counterpartyCompanyId);
        List<Entry> entries = jdbc.query("""
                        SELECT entry.id, entry.contract_id, entry.source_type, entry.source_id,
                               entry.business_date, entry.document_no, entry.amount,
                               entry.supplier_company_id, entry.buyer_company_id,
                               entry.issuer_company_id, entry.approved_at,
                               contract.contract_no
                        FROM reconciliation_entry entry
                        LEFT JOIN trade_contract contract ON contract.id = entry.contract_id
                        WHERE entry.company_a_id = ? AND entry.company_b_id = ?
                        ORDER BY entry.business_date DESC, entry.approved_at DESC, entry.id DESC
                        """, (rs, rowNum) -> new Entry(
                        rs.getLong("id"), rs.getLong("contract_id"),
                        rs.getString("source_type"), rs.getLong("source_id"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getString("document_no"), rs.getBigDecimal("amount"),
                        rs.getLong("supplier_company_id"), rs.getLong("buyer_company_id"),
                        rs.getLong("issuer_company_id"),
                        rs.getTimestamp("approved_at").toLocalDateTime(),
                        rs.getString("contract_no")), companyAId, companyBId);

        BigDecimal mySales = BigDecimal.ZERO;
        BigDecimal myPurchases = BigDecimal.ZERO;
        BigDecimal issuedInvoices = BigDecimal.ZERO;
        BigDecimal receivedInvoices = BigDecimal.ZERO;
        BigDecimal receivedPayments = BigDecimal.ZERO;
        BigDecimal paidPayments = BigDecimal.ZERO;
        LocalDateTime updatedAt = null;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Entry entry : entries) {
            boolean mySale = entry.supplierCompanyId() == companyId;
            if (SALES_ORDER.equals(entry.sourceType())) {
                if (mySale) mySales = mySales.add(entry.amount());
                else myPurchases = myPurchases.add(entry.amount());
            } else if (INVOICE.equals(entry.sourceType())) {
                if (mySale) issuedInvoices = issuedInvoices.add(entry.amount());
                else receivedInvoices = receivedInvoices.add(entry.amount());
            } else if (PAYMENT_VOUCHER.equals(entry.sourceType())) {
                if (mySale) receivedPayments = receivedPayments.add(entry.amount());
                else paidPayments = paidPayments.add(entry.amount());
            }
            if (updatedAt == null || entry.approvedAt().isAfter(updatedAt)) updatedAt = entry.approvedAt();
            if (includeEntries) detail.add(entryView(entry, mySale));
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("counterpartyCompanyId", counterpartyCompanyId);
        view.put("counterpartyName", counterpartyName);
        view.put("mySalesAmount", money(mySales));
        view.put("myPurchaseAmount", money(myPurchases));
        view.put("issuedInvoiceAmount", money(issuedInvoices));
        view.put("receivedInvoiceAmount", money(receivedInvoices));
        view.put("receivedPaymentAmount", money(receivedPayments));
        view.put("paidPaymentAmount", money(paidPayments));
        view.put("receivableBalance", money(mySales.subtract(receivedPayments)));
        view.put("payableBalance", money(myPurchases.subtract(paidPayments)));
        view.put("unbilledAmount", money(mySales.subtract(issuedInvoices)));
        view.put("unreceivedInvoiceAmount", money(myPurchases.subtract(receivedInvoices)));
        view.put("entryCount", entries.size());
        view.put("updatedAt", updatedAt);
        if (includeEntries) view.put("entries", detail);
        return view;
    }

    private Map<String, Object> entryView(Entry entry, boolean mySale) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", entry.id());
        view.put("contractId", entry.contractId());
        view.put("contractNo", safe(entry.contractNo()));
        view.put("sourceType", entry.sourceType());
        view.put("sourceTypeText", switch (entry.sourceType()) {
            case SALES_ORDER -> "销售单";
            case PAYMENT_VOUCHER -> "转款凭证";
            case INVOICE -> "发票";
            default -> entry.sourceType();
        });
        view.put("sourceId", entry.sourceId());
        view.put("businessDate", entry.businessDate());
        view.put("documentNo", entry.documentNo());
        view.put("amount", money(entry.amount()));
        view.put("direction", mySale ? "SALE" : "PURCHASE");
        view.put("directionText", mySale ? "我方销售" : "我方采购");
        view.put("approvedAt", entry.approvedAt());
        return view;
    }

    private void requireRelation(long companyId, Long counterpartyCompanyId) {
        if (counterpartyCompanyId == null || companyId == counterpartyCompanyId) {
            throw new BusinessException("请选择合作企业");
        }
        if (relationMapper.countActiveBetween(companyId, counterpartyCompanyId) == 0) {
            throw new BusinessException("合作企业关系不存在");
        }
    }

    byte[] generateWorkbook(String title, List<ContractAccount> contracts,
                            Map<Long, List<WorkbookEntry>> entriesByContract) {
        try (InputStream input = new ClassPathResource(WORKBOOK_TEMPLATE).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            int requiredRows = contracts.stream()
                    .mapToInt(contract -> rowsForContract(entriesByContract.get(contract.id())))
                    .sum();
            int dataRows = Math.max(TEMPLATE_DATA_ROWS, requiredRows);
            int totalRowIndex = DATA_START_ROW + dataRows;
            if (dataRows > TEMPLATE_DATA_ROWS) {
                int extraRows = dataRows - TEMPLATE_DATA_ROWS;
                sheet.shiftRows(TEMPLATE_TOTAL_ROW, sheet.getLastRowNum(), extraRows, true, false);
                Row styleSource = sheet.getRow(TEMPLATE_TOTAL_ROW - 1);
                for (int rowIndex = TEMPLATE_TOTAL_ROW; rowIndex < totalRowIndex; rowIndex++) {
                    copyRowStyle(styleSource, sheet.createRow(rowIndex));
                }
            }

            cell(sheet.getRow(3), 2).setCellValue(title);
            clearDataRows(sheet, DATA_START_ROW, totalRowIndex);
            int rowIndex = DATA_START_ROW;
            for (ContractAccount contract : contracts) {
                List<WorkbookEntry> entries = entriesByContract.getOrDefault(contract.id(), List.of());
                List<WorkbookEntry> sales = entriesOfType(entries, SALES_ORDER);
                List<WorkbookEntry> payments = entriesOfType(entries, PAYMENT_VOUCHER);
                List<WorkbookEntry> invoices = entriesOfType(entries, INVOICE);
                int contractRows = Math.max(1, Math.max(sales.size(), Math.max(payments.size(), invoices.size())));
                Row firstRow = sheet.getRow(rowIndex);
                if (contract.date() != null) {
                    Cell dateCell = cell(firstRow, 2);
                    dateCell.setCellStyle(cell(sheet.getRow(DATA_START_ROW), 2).getCellStyle());
                    dateCell.setCellValue(contract.date());
                }
                cell(firstRow, 3).setCellValue(safe(contract.contractNo()));
                setMoney(cell(firstRow, 4), contract.amount());
                for (int offset = 0; offset < contractRows; offset++) {
                    Row row = sheet.getRow(rowIndex + offset);
                    if (offset < sales.size()) setMoney(cell(row, 5), sales.get(offset).amount());
                    if (offset < payments.size()) setMoney(cell(row, 6), payments.get(offset).amount());
                    if (offset < invoices.size()) setMoney(cell(row, 8), invoices.get(offset).amount());
                }
                int firstExcelRow = rowIndex + 1;
                int lastExcelRow = rowIndex + contractRows;
                cell(firstRow, 7).setCellFormula("SUM(F" + firstExcelRow + ":F" + lastExcelRow
                        + ")-SUM(G" + firstExcelRow + ":G" + lastExcelRow + ")");
                cell(firstRow, 9).setCellFormula("SUM(F" + firstExcelRow + ":F" + lastExcelRow
                        + ")-SUM(I" + firstExcelRow + ":I" + lastExcelRow + ")");
                rowIndex += contractRows;
            }

            Row totalRow = sheet.getRow(totalRowIndex);
            cell(totalRow, 2).setCellValue("合计");
            cell(totalRow, 3).setBlank();
            int firstDataExcelRow = DATA_START_ROW + 1;
            int lastDataExcelRow = totalRowIndex;
            for (int column = 4; column <= 9; column++) {
                String letter = String.valueOf((char) ('A' + column));
                cell(totalRow, column).setCellFormula("SUM(" + letter + firstDataExcelRow
                        + ":" + letter + lastDataExcelRow + ")");
            }
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            sheet.setForceFormulaRecalculation(true);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("自动对账单生成失败，请稍后重试");
        }
    }

    private int rowsForContract(List<WorkbookEntry> entries) {
        if (entries == null || entries.isEmpty()) return 1;
        int sales = 0;
        int payments = 0;
        int invoices = 0;
        for (WorkbookEntry entry : entries) {
            if (SALES_ORDER.equals(entry.sourceType())) sales++;
            else if (PAYMENT_VOUCHER.equals(entry.sourceType())) payments++;
            else if (INVOICE.equals(entry.sourceType())) invoices++;
        }
        return Math.max(1, Math.max(sales, Math.max(payments, invoices)));
    }

    private List<WorkbookEntry> entriesOfType(List<WorkbookEntry> entries, String sourceType) {
        return entries.stream().filter(entry -> sourceType.equals(entry.sourceType())).toList();
    }

    private void clearDataRows(Sheet sheet, int startRow, int endRow) {
        for (int rowIndex = startRow; rowIndex < endRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            for (int column = 2; column <= 9; column++) cell(row, column).setBlank();
        }
    }

    private void copyRowStyle(Row source, Row target) {
        target.setHeight(source.getHeight());
        for (int column = 0; column <= 9; column++) {
            Cell sourceCell = source.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            Cell targetCell = target.createCell(column, CellType.BLANK);
            targetCell.setCellStyle(sourceCell.getCellStyle());
        }
    }

    private Cell cell(Row row, int column) {
        return row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
    }

    private void setMoney(Cell cell, BigDecimal value) {
        cell.setCellValue(money(value).doubleValue());
    }

    private String safeFileName(String value) {
        return safe(value).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    private long supplierCompanyId(TradeContract contract) {
        if ("PURCHASE".equalsIgnoreCase(contract.getDirection())) {
            return contract.getCounterpartyCompanyId();
        }
        return contract.getCompanyId();
    }

    private long buyerCompanyId(TradeContract contract) {
        return "PURCHASE".equalsIgnoreCase(contract.getDirection())
                ? contract.getCompanyId() : contract.getCounterpartyCompanyId();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record Entry(Long id, Long contractId, String sourceType, Long sourceId,
                         LocalDate businessDate, String documentNo, BigDecimal amount,
                         long supplierCompanyId, long buyerCompanyId, long issuerCompanyId,
                         LocalDateTime approvedAt, String contractNo) {
    }

    record ContractAccount(Long id, String contractNo, BigDecimal amount, LocalDate date) {
    }

    record WorkbookEntry(String sourceType, LocalDate date, BigDecimal amount) {
    }

    public record WorkbookPayload(String originalName, String contentType, byte[] data) {
    }
}
