package com.tradepass.service;

import com.tradepass.common.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApprovalService {
    private final JdbcTemplate jdbc;
    private final AccessControlService accessControlService;

    public ApprovalService(JdbcTemplate jdbc, AccessControlService accessControlService) {
        this.jdbc = jdbc;
        this.accessControlService = accessControlService;
    }

    public List<Map<String, Object>> pendingFulfillment() {
        long companyId = AuthContext.requireCompanyId();
        List<Map<String, Object>> items = new ArrayList<>();
        if (accessControlService.hasPermission(companyId, "sales_order_receive")) {
            items.addAll(pendingSalesOrders(companyId));
        }
        if (canReviewAttachments(companyId)) {
            items.addAll(pendingAttachments(companyId));
        }
        items.sort(Comparator.comparing(
                item -> String.valueOf(item.getOrDefault("createdAt", "")),
                Comparator.reverseOrder()));
        return items;
    }

    private boolean canReviewAttachments(long companyId) {
        return accessControlService.hasPermission(companyId, "contract_attachment_upload")
                || accessControlService.hasPermission(companyId, "reconciliation")
                || accessControlService.hasPermission(companyId, "invoice_view");
    }

    private List<Map<String, Object>> pendingSalesOrders(long companyId) {
        return jdbc.query("""
                        SELECT document.id, document.contract_id, document.document_no,
                               document.created_at, document.company_id AS source_company_id,
                               company.name AS source_company_name,
                               contract.contract_no, contract.name AS contract_name
                        FROM business_document document
                        JOIN company ON company.id = document.company_id
                        JOIN trade_contract contract ON contract.id = document.contract_id
                        WHERE document.recipient_company_id = ?
                          AND document.document_type = 'SALES_ORDER'
                          AND document.status = 'ISSUED'
                        ORDER BY document.created_at DESC, document.id DESC
                        """, (rs, rowNum) -> item(
                        rs.getLong("id"), "SALES_ORDER", "销售单",
                        rs.getLong("source_company_id"), rs.getString("source_company_name"),
                        rs.getLong("contract_id"), rs.getString("contract_no"),
                        rs.getString("contract_name"), rs.getString("document_no"),
                        null, null, rs.getTimestamp("created_at")), companyId);
    }

    private List<Map<String, Object>> pendingAttachments(long companyId) {
        return jdbc.query("""
                        SELECT attachment.id, attachment.contract_id, attachment.category,
                               attachment.original_name, attachment.content_type, attachment.voucher_date,
                               attachment.voucher_amount, attachment.invoice_date,
                               attachment.invoice_amount, attachment.created_at,
                               attachment.uploader_company_id AS source_company_id,
                               company.name AS source_company_name,
                               contract.contract_no, contract.name AS contract_name
                        FROM contract_attachment attachment
                        JOIN company ON company.id = attachment.uploader_company_id
                        JOIN trade_contract contract ON contract.id = attachment.contract_id
                        WHERE attachment.recipient_company_id = ?
                          AND attachment.status = 'PENDING_CONFIRMATION'
                          AND attachment.category IN ('PAYMENT_VOUCHER', 'INVOICE')
                        ORDER BY attachment.created_at DESC, attachment.id DESC
                        """, (rs, rowNum) -> {
                    String category = rs.getString("category");
                    boolean invoice = ContractAttachmentService.INVOICE.equals(category);
                    LocalDate businessDate = rs.getObject(
                            invoice ? "invoice_date" : "voucher_date", LocalDate.class);
                    BigDecimal amount = rs.getBigDecimal(
                            invoice ? "invoice_amount" : "voucher_amount");
                    Map<String, Object> view = item(rs.getLong("id"), category,
                            invoice ? "发票" : "转款凭证",
                            rs.getLong("source_company_id"), rs.getString("source_company_name"),
                            rs.getLong("contract_id"), rs.getString("contract_no"),
                            rs.getString("contract_name"), rs.getString("original_name"),
                            businessDate, amount, rs.getTimestamp("created_at"));
                    String contentType = rs.getString("content_type");
                    view.put("contentType", contentType == null ? "" : contentType);
                    view.put("isImage", contentType != null && contentType.startsWith("image/"));
                    return view;
                }, companyId);
    }

    private Map<String, Object> item(long id, String approvalType, String typeText,
                                     long sourceCompanyId, String sourceCompanyName,
                                     long contractId, String contractNo, String contractName,
                                     String documentNo, LocalDate businessDate,
                                     BigDecimal amount, Timestamp createdAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("approvalType", approvalType);
        item.put("typeText", typeText);
        item.put("sourceCompanyId", sourceCompanyId);
        item.put("sourceCompanyName", sourceCompanyName);
        item.put("contractId", contractId);
        item.put("contractNo", contractNo == null ? "" : contractNo);
        item.put("contractName", contractName == null ? "" : contractName);
        item.put("documentNo", documentNo == null ? "" : documentNo);
        item.put("businessDate", businessDate);
        item.put("amount", amount);
        LocalDateTime created = createdAt == null ? null : createdAt.toLocalDateTime();
        item.put("createdAt", created);
        return item;
    }
}
