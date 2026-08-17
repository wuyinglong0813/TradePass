package com.tradepass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.BusinessDocument;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.BusinessDocumentMapper;
import com.tradepass.mapper.TradeContractMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SalesOrderInventoryService {
    public static final String RECEIVE_ONLY = "RECEIVE_ONLY";
    public static final String INBOUND = "INBOUND";
    public static final String REJECT = "REJECT";

    private final JdbcTemplate jdbc;
    private final BusinessDocumentMapper documentMapper;
    private final TradeContractMapper contractMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ReconciliationAccountService reconciliationAccountService;
    private final SalesOrderSignatureService signatureService;
    private final UserIdentityService userIdentityService;

    @Autowired
    public SalesOrderInventoryService(JdbcTemplate jdbc,
                                      BusinessDocumentMapper documentMapper,
                                      TradeContractMapper contractMapper,
                                      AccessControlService accessControlService,
                                      AuditLogService auditLogService,
                                      ObjectMapper objectMapper,
                                      ReconciliationAccountService reconciliationAccountService,
                                      SalesOrderSignatureService signatureService,
                                      UserIdentityService userIdentityService) {
        this.jdbc = jdbc;
        this.documentMapper = documentMapper;
        this.contractMapper = contractMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.reconciliationAccountService = reconciliationAccountService;
        this.signatureService = signatureService;
        this.userIdentityService = userIdentityService;
    }

    SalesOrderInventoryService(JdbcTemplate jdbc,
                               BusinessDocumentMapper documentMapper,
                               TradeContractMapper contractMapper,
                               AccessControlService accessControlService,
                               AuditLogService auditLogService,
                               ObjectMapper objectMapper) {
        this(jdbc, documentMapper, contractMapper, accessControlService, auditLogService,
                objectMapper, null, null, null);
    }

    @Transactional
    public void saveDocumentItems(BusinessDocument document) {
        if (document == null || document.getId() == null || document.getRecipientCompanyId() == null) return;
        List<SalesItem> items = parseItems(document.getContent());
        if (items.isEmpty()) throw new BusinessException("销售单至少需要一项有效商品");
        jdbc.update("DELETE FROM business_document_item WHERE document_id = ?", document.getId());
        for (SalesItem item : items) {
            jdbc.update("""
                    INSERT INTO business_document_item
                    (document_id, issuer_company_id, recipient_company_id, line_no, product_name,
                     specification, base_unit, quantity, unit_price, amount, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, document.getId(), document.getCompanyId(), document.getRecipientCompanyId(),
                    item.lineNo(), item.productName(), item.specification(), item.baseUnit(),
                    item.quantity(), item.unitPrice(), item.amount(), item.remark());
        }
    }

    public Map<String, Object> documentDetail(Long documentId) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId,
                "contract_view", "contract_sign", "order_create", "sales_order_receive", "inventory_view");
        BusinessDocument document = requireDocumentParty(documentId, companyId);
        boolean recipient = Long.valueOf(companyId).equals(document.getRecipientCompanyId());
        boolean owner = Long.valueOf(companyId).equals(document.getCompanyId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", document.getId());
        view.put("contractId", document.getContractId());
        view.put("issuerCompanyId", document.getCompanyId());
        view.put("recipientCompanyId", document.getRecipientCompanyId());
        view.put("documentNo", document.getDocumentNo());
        view.put("templateName", document.getTemplateName());
        view.put("sourceType", document.getSourceType());
        view.put("status", document.getStatus());
        view.put("statusText", "ISSUED".equals(document.getStatus()) && recipient
                ? "待我方确认" : statusText(document.getStatus()));
        view.put("createdAt", document.getCreatedAt());
        view.put("acknowledgedAt", document.getAcknowledgedAt());
        view.put("rejectedReason", document.getRejectedReason() == null ? "" : document.getRejectedReason());
        try {
            view.put("content", objectMapper.readTree(document.getContent()));
        } catch (Exception exception) {
            view.put("content", Map.of());
        }
        view.put("items", documentItems(documentId));
        TradeContract contract = contractMapper.selectById(document.getContractId());
        boolean canReceive = recipient && accessControlService.hasPermission(companyId, "sales_order_receive");
        boolean canInbound = canReceive && accessControlService.hasPermission(companyId, "inventory_receive");
        view.put("canReceive", canReceive && "ISSUED".equals(document.getStatus()));
        view.put("canReject", canReceive && "ISSUED".equals(document.getStatus()));
        view.put("canInbound", canInbound && ("ISSUED".equals(document.getStatus())
                || "ACKNOWLEDGED".equals(document.getStatus())));
        view.put("contractStatus", contract == null ? "" : contract.getStatus());
        boolean editable = "DRAFT".equals(document.getStatus()) || "REJECTED".equals(document.getStatus());
        view.put("canEditDraft", owner && editable && contract != null
                && ("PENDING".equals(contract.getStatus()) || "ACTIVE".equals(contract.getStatus())));
        view.put("canPublish", owner && editable && contract != null
                && "ACTIVE".equals(contract.getStatus()));
        return view;
    }

    public List<Map<String, Object>> listWarehouses() {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "inventory_view", "inventory_receive");
        return jdbc.query("""
                        SELECT id, name, address, enabled, created_at
                        FROM warehouse WHERE company_id = ? AND enabled = 1
                        ORDER BY created_at, id
                        """, (rs, rowNum) -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", rs.getLong("id"));
                    view.put("name", rs.getString("name"));
                    view.put("address", rs.getString("address"));
                    view.put("enabled", rs.getBoolean("enabled"));
                    view.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
                    return view;
                }, companyId);
    }

    @Transactional
    public Map<String, Object> createWarehouse(String name, String address) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "inventory_receive");
        String safeName = name == null ? "" : name.trim();
        String safeAddress = address == null ? "" : address.trim();
        if (safeName.isBlank() || safeName.length() > 128) throw new BusinessException("仓库名称不能为空且不能超过 128 字");
        if (safeAddress.length() > 256) throw new BusinessException("仓库地址不能超过 256 字");
        try {
            jdbc.update("""
                    INSERT INTO warehouse (company_id, name, address, created_by)
                    VALUES (?, ?, ?, ?)
                    """, companyId, safeName, safeAddress, AuthContext.userId());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("仓库名称已存在");
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditLogService.log(companyId, "WAREHOUSE", id, "CREATE", "创建仓库 " + safeName);
        return listWarehouses().stream().filter(item -> id != null && id.equals(item.get("id")))
                .findFirst().orElseThrow(() -> new BusinessException("仓库创建失败"));
    }

    public Map<String, Object> inventoryOverview() {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "inventory_view");
        List<Map<String, Object>> balances = jdbc.query("""
                        SELECT balance.id, warehouse.id AS warehouse_id, warehouse.name AS warehouse_name,
                               product.id AS product_id, product.product_name, product.specification,
                               product.base_unit, balance.quantity, balance.unit_price,
                               balance.inventory_amount, balance.updated_at
                        FROM inventory_balance balance
                        JOIN warehouse ON warehouse.id = balance.warehouse_id
                        JOIN inventory_product product ON product.id = balance.product_id
                        WHERE balance.company_id = ? AND balance.quantity <> 0
                        ORDER BY warehouse.name, product.product_name, product.specification
                        """, (rs, rowNum) -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", rs.getLong("id"));
                    view.put("warehouseId", rs.getLong("warehouse_id"));
                    view.put("warehouseName", rs.getString("warehouse_name"));
                    view.put("productId", rs.getLong("product_id"));
                    view.put("productName", rs.getString("product_name"));
                    view.put("specification", rs.getString("specification"));
                    view.put("baseUnit", rs.getString("base_unit"));
                    view.put("quantity", rs.getBigDecimal("quantity"));
                    view.put("unitPrice", rs.getBigDecimal("unit_price"));
                    view.put("inventoryAmount", rs.getBigDecimal("inventory_amount"));
                    view.put("updatedAt", rs.getTimestamp("updated_at").toLocalDateTime());
                    return view;
                }, companyId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warehouseCount", jdbc.queryForObject(
                "SELECT COUNT(1) FROM warehouse WHERE company_id = ? AND enabled = 1", Long.class, companyId));
        result.put("productCount", balances.size());
        result.put("balances", balances);
        return result;
    }

    @Transactional
    public Map<String, Object> receive(Long documentId, String decision, Long warehouseId) {
        return receive(documentId, decision, warehouseId, null, null, null);
    }

    @Transactional
    public Map<String, Object> receive(Long documentId, String decision, Long warehouseId,
                                       String rejectedReason) {
        return receive(documentId, decision, warehouseId, rejectedReason, null, null);
    }

    @Transactional
    public Map<String, Object> receive(Long documentId, String decision, Long warehouseId,
                                       String rejectedReason, String signatureName,
                                       byte[] signatureData) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "sales_order_receive");
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!RECEIVE_ONLY.equals(normalized) && !INBOUND.equals(normalized) && !REJECT.equals(normalized)) {
            throw new BusinessException("接收方式不正确");
        }
        BusinessDocument document = documentMapper.selectById(documentId);
        if (document == null || !"SALES_ORDER".equals(document.getDocumentType())
                || !Long.valueOf(companyId).equals(document.getRecipientCompanyId())) {
            throw new BusinessException("待接收销售单不存在");
        }
        if (REJECT.equals(normalized)) {
            if (!"ISSUED".equals(document.getStatus())) {
                throw new BusinessException("销售单当前状态不能驳回");
            }
            String reason = rejectedReason == null ? "" : rejectedReason.trim();
            if (reason.isBlank() || reason.length() > 500) {
                throw new BusinessException("请输入驳回原因且不能超过 500 字");
            }
            document.setStatus("REJECTED");
            document.setRejectedReason(reason);
            documentMapper.updateById(document);
            auditLogService.log(companyId, "SALES_ORDER_RECEIPT", documentId,
                    "REJECT", "驳回销售单 " + document.getDocumentNo() + "：" + reason);
            return documentDetail(documentId);
        }
        if ("INBOUNDED".equals(document.getStatus())) return documentDetail(documentId);
        if (!"ISSUED".equals(document.getStatus()) && !"ACKNOWLEDGED".equals(document.getStatus())) {
            throw new BusinessException("销售单尚未发布，不能接收或入库");
        }
        if (RECEIVE_ONLY.equals(normalized) && "ACKNOWLEDGED".equals(document.getStatus())) {
            return documentDetail(documentId);
        }

        boolean requiresSignature = "ISSUED".equals(document.getStatus());
        String signerName = null;
        if (requiresSignature) {
            if (signatureData == null || signatureData.length == 0) {
                throw new BusinessException("请先完成手写签名");
            }
            signerName = userIdentityService == null
                    ? "用户" + AuthContext.userId()
                    : userIdentityService.requireCurrentVerifiedName(companyId);
        }
        if (INBOUND.equals(normalized)) {
            accessControlService.requirePermission(companyId, "inventory_receive");
            requireWarehouse(companyId, warehouseId);
        }

        Long receiptId = existingReceipt(companyId, documentId);
        if (receiptId == null) {
            jdbc.update("""
                    INSERT INTO sales_order_receipt
                    (company_id, document_id, decision, status, received_by)
                    VALUES (?, ?, ?, ?, ?)
                    """, companyId, documentId, normalized,
                    INBOUND.equals(normalized) ? "INBOUNDING" : "RECEIVED_PENDING_INBOUND",
                    AuthContext.userId());
            receiptId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            jdbc.update("UPDATE sales_order_receipt SET decision = ?, status = ? WHERE id = ?",
                    normalized, INBOUND.equals(normalized) ? "INBOUNDING" : "RECEIVED_PENDING_INBOUND", receiptId);
        }
        if (requiresSignature) {
            if (signatureService == null) {
                throw new BusinessException("销售单签名服务尚未启用");
            }
            signatureService.save(companyId, documentId, receiptId, signerName,
                    signatureName, signatureData);
        }

        if (RECEIVE_ONLY.equals(normalized)) {
            document.setStatus("ACKNOWLEDGED");
            document.setAcknowledgedBy(AuthContext.userId());
            document.setAcknowledgedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            recordReconciliation(document);
            auditLogService.log(companyId, "SALES_ORDER_RECEIPT", receiptId,
                    "RECEIVE", "接收销售单 " + document.getDocumentNo() + "，暂不入库");
            return documentDetail(documentId);
        }

        Long existingInbound = jdbc.query("""
                        SELECT id FROM inventory_inbound
                        WHERE company_id = ? AND source_document_id = ? LIMIT 1
                        """, rs -> rs.next() ? rs.getLong(1) : null, companyId, documentId);
        if (existingInbound != null) {
            document.setStatus("INBOUNDED");
            documentMapper.updateById(document);
            recordReconciliation(document);
            return documentDetail(documentId);
        }

        String inboundNo = createInboundNo();
        jdbc.update("""
                INSERT INTO inventory_inbound
                (company_id, warehouse_id, source_document_id, inbound_no, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, companyId, warehouseId, documentId, inboundNo, AuthContext.userId());
        Long inboundId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        List<Map<String, Object>> items = documentItems(documentId);
        if (items.isEmpty()) throw new BusinessException("销售单没有可入库商品");
        for (Map<String, Object> item : items) {
            BigDecimal quantity = (BigDecimal) item.get("quantity");
            if (quantity == null || quantity.signum() <= 0) throw new BusinessException("销售单商品数量必须大于 0");
            BigDecimal unitPrice = (BigDecimal) item.get("unitPrice");
            if (unitPrice == null || unitPrice.signum() < 0) throw new BusinessException("销售单商品单价不能小于 0");
            BigDecimal inboundAmount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            Long productId = findOrCreateProduct(companyId,
                    String.valueOf(item.get("productName")), String.valueOf(item.get("specification")),
                    String.valueOf(item.get("baseUnit")));
            jdbc.update("""
                    INSERT INTO inventory_balance
                    (company_id, warehouse_id, product_id, quantity, unit_price, inventory_amount)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        unit_price = CASE
                            WHEN quantity + VALUES(quantity) = 0 THEN 0
                            ELSE (inventory_amount + VALUES(inventory_amount))
                                / (quantity + VALUES(quantity))
                        END,
                        inventory_amount = inventory_amount + VALUES(inventory_amount),
                        quantity = quantity + VALUES(quantity),
                        updated_at = CURRENT_TIMESTAMP
                    """, companyId, warehouseId, productId, quantity, unitPrice, inboundAmount);
            BigDecimal balance = jdbc.queryForObject("""
                    SELECT quantity FROM inventory_balance
                    WHERE company_id = ? AND warehouse_id = ? AND product_id = ?
                    """, BigDecimal.class, companyId, warehouseId, productId);
            jdbc.update("""
                    INSERT INTO inventory_inbound_item
                    (inbound_id, document_item_id, product_id, quantity, unit_price, amount)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, inboundId, item.get("id"), productId, quantity, unitPrice, inboundAmount);
            jdbc.update("""
                    INSERT INTO inventory_transaction
                    (company_id, warehouse_id, product_id, biz_type, biz_id,
                     quantity_delta, balance_after, created_by)
                    VALUES (?, ?, ?, 'SALES_ORDER_INBOUND', ?, ?, ?, ?)
                    """, companyId, warehouseId, productId, inboundId, quantity, balance, AuthContext.userId());
        }
        jdbc.update("UPDATE sales_order_receipt SET decision = 'INBOUND', status = 'INBOUNDED' WHERE id = ?", receiptId);
        document.setStatus("INBOUNDED");
        document.setAcknowledgedBy(AuthContext.userId());
        document.setAcknowledgedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        recordReconciliation(document);
        auditLogService.log(companyId, "INVENTORY_INBOUND", inboundId,
                "CREATE", "销售单 " + document.getDocumentNo() + " 接收并入库至仓库 " + warehouseId);
        return documentDetail(documentId);
    }

    private BusinessDocument requireDocumentParty(Long documentId, long companyId) {
        BusinessDocument document = documentMapper.selectById(documentId);
        if (document == null || !"SALES_ORDER".equals(document.getDocumentType())) {
            throw new BusinessException("销售单不存在");
        }
        TradeContract contract = contractMapper.selectById(document.getContractId());
        if (contract == null || (!Long.valueOf(companyId).equals(contract.getCompanyId())
                && !Long.valueOf(companyId).equals(contract.getCounterpartyCompanyId()))) {
            throw new BusinessException("销售单不存在");
        }
        if ("DRAFT".equals(document.getStatus())
                && !Long.valueOf(companyId).equals(document.getCompanyId())) {
            throw new BusinessException("销售单不存在");
        }
        return document;
    }

    private List<Map<String, Object>> documentItems(Long documentId) {
        return jdbc.query("""
                        SELECT id, line_no, product_name, specification, base_unit,
                               quantity, unit_price, amount, remark
                        FROM business_document_item WHERE document_id = ? ORDER BY line_no
                        """, (rs, rowNum) -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", rs.getLong("id"));
                    view.put("lineNo", rs.getInt("line_no"));
                    view.put("productName", rs.getString("product_name"));
                    view.put("specification", rs.getString("specification"));
                    view.put("baseUnit", rs.getString("base_unit"));
                    view.put("quantity", rs.getBigDecimal("quantity"));
                    view.put("unitPrice", rs.getBigDecimal("unit_price"));
                    view.put("amount", rs.getBigDecimal("amount"));
                    view.put("remark", rs.getString("remark"));
                    return view;
                }, documentId);
    }

    private List<SalesItem> parseItems(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<String> columns = new ArrayList<>();
            root.path("columns").forEach(node -> columns.add(node.asText("")));
            int productIndex = findColumn(columns, List.of("品名", "产品", "名称"));
            int specIndex = findColumn(columns, List.of("规格", "型号"));
            int unitIndex = findColumn(columns, List.of("单位"));
            int quantityIndex = findColumn(columns, List.of("数量"));
            int priceIndex = findColumn(columns, List.of("单价"));
            int amountIndex = findColumn(columns, List.of("金额"));
            int remarkIndex = findColumn(columns, List.of("备注"));
            List<SalesItem> items = new ArrayList<>();
            int lineNo = 1;
            for (JsonNode row : root.path("rows")) {
                String product = cell(row, productIndex).trim();
                BigDecimal quantity = decimal(cell(row, quantityIndex), 4);
                BigDecimal price = decimal(cell(row, priceIndex), 6);
                BigDecimal amount = decimal(cell(row, amountIndex), 2);
                if (product.isBlank() && quantity.signum() == 0 && amount.signum() == 0) continue;
                if (product.isBlank()) throw new BusinessException("销售单商品名称不能为空");
                if (quantity.signum() <= 0) throw new BusinessException("销售单商品数量必须大于 0");
                if (amount.signum() == 0 && price.signum() != 0) {
                    amount = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
                }
                items.add(new SalesItem(lineNo++, product, cell(row, specIndex).trim(),
                        defaultText(cell(row, unitIndex), "件"), quantity, price, amount,
                        cell(row, remarkIndex).trim()));
            }
            return items;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("销售单商品明细格式不正确");
        }
    }

    private int findColumn(List<String> columns, List<String> aliases) {
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            if (aliases.stream().anyMatch(column::contains)) return i;
        }
        return -1;
    }

    private String cell(JsonNode row, int index) {
        return index >= 0 && row.isArray() && index < row.size() ? row.path(index).asText("") : "";
    }

    private BigDecimal decimal(String value, int scale) {
        try {
            String normalized = value == null ? "" : value.replace(",", "").trim();
            if (normalized.isBlank()) return BigDecimal.ZERO.setScale(scale);
            return new BigDecimal(normalized).setScale(scale, RoundingMode.HALF_UP);
        } catch (Exception exception) {
            throw new BusinessException("销售单数量或金额格式不正确");
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Long existingReceipt(long companyId, Long documentId) {
        return jdbc.query("SELECT id FROM sales_order_receipt WHERE company_id = ? AND document_id = ? LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, companyId, documentId);
    }

    private void requireWarehouse(long companyId, Long warehouseId) {
        if (warehouseId == null) throw new BusinessException("请选择入库仓库");
        Long count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM warehouse WHERE id = ? AND company_id = ? AND enabled = 1
                """, Long.class, warehouseId, companyId);
        if (count == null || count == 0) throw new BusinessException("仓库不存在");
    }

    private Long findOrCreateProduct(long companyId, String name, String specification, String unit) {
        String safeSpec = specification == null || "null".equals(specification) ? "" : specification.trim();
        List<Long> existing = jdbc.query("""
                        SELECT id FROM inventory_product
                        WHERE company_id = ? AND product_name = ? AND specification = ? AND base_unit = ? LIMIT 1
                        """, (rs, rowNum) -> rs.getLong(1), companyId, name, safeSpec, unit);
        if (!existing.isEmpty()) return existing.get(0);
        try {
            jdbc.update("""
                    INSERT INTO inventory_product (company_id, product_name, specification, base_unit)
                    VALUES (?, ?, ?, ?)
                    """, companyId, name, safeSpec, unit);
            return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } catch (DuplicateKeyException exception) {
            return jdbc.queryForObject("""
                    SELECT id FROM inventory_product
                    WHERE company_id = ? AND product_name = ? AND specification = ? AND base_unit = ?
                    """, Long.class, companyId, name, safeSpec, unit);
        }
    }

    private String createInboundNo() {
        return "RK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String statusText(String status) {
        if ("DRAFT".equals(status)) return "草稿";
        if ("ISSUED".equals(status)) return "待对方确认";
        if ("REJECTED".equals(status)) return "已驳回";
        if ("ACKNOWLEDGED".equals(status)) return "已通过待入库";
        if ("INBOUNDED".equals(status)) return "已通过并入库";
        return status;
    }

    private void recordReconciliation(BusinessDocument document) {
        if (reconciliationAccountService == null) return;
        List<SalesItem> items = parseItems(document.getContent());
        BigDecimal total = items.stream().map(SalesItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        LocalDate businessDate = LocalDate.now();
        try {
            String date = objectMapper.readTree(document.getContent()).path("date").asText("");
            if (!date.isBlank()) businessDate = LocalDate.parse(date);
        } catch (Exception ignored) {
            // 历史销售单日期缺失时以确认当天作为业务日期。
        }
        reconciliationAccountService.recordSalesOrder(document, total, businessDate,
                AuthContext.userId(), document.getAcknowledgedAt());
    }

    private record SalesItem(int lineNo, String productName, String specification,
                             String baseUnit, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal amount, String remark) {
    }
}
