package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.BusinessDocument;
import com.tradepass.entity.BusinessDocumentTemplate;
import com.tradepass.entity.Company;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.BusinessDocumentMapper;
import com.tradepass.mapper.BusinessDocumentTemplateMapper;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.TradeContractMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessDocumentService {
    public static final String SALES_ORDER = "SALES_ORDER";
    public static final String DELIVERY_NOTE = "DELIVERY_NOTE";

    private final BusinessDocumentTemplateMapper templateMapper;
    private final BusinessDocumentMapper documentMapper;
    private final TradeContractMapper contractMapper;
    private final CompanyMapper companyMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public BusinessDocumentService(BusinessDocumentTemplateMapper templateMapper,
                                   BusinessDocumentMapper documentMapper,
                                   TradeContractMapper contractMapper,
                                   CompanyMapper companyMapper,
                                   AccessControlService accessControlService,
                                   AuditLogService auditLogService,
                                   ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.documentMapper = documentMapper;
        this.contractMapper = contractMapper;
        this.companyMapper = companyMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTemplates(String type) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(
                companyId, "contract_template", "contract_sign", "contract_view");
        String normalizedType = normalizeType(type);
        return templateMapper.selectList(new LambdaQueryWrapper<BusinessDocumentTemplate>()
                        .eq(BusinessDocumentTemplate::getCompanyId, companyId)
                        .eq(BusinessDocumentTemplate::getDocumentType, normalizedType)
                        .orderByDesc(BusinessDocumentTemplate::getUpdatedAt)
                        .orderByDesc(BusinessDocumentTemplate::getId))
                .stream().map(this::templateView).toList();
    }

    @Transactional
    public Map<String, Object> createTemplate(Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        String type = normalizeType(string(body.get("documentType")));
        String name = string(body.get("name")).trim();
        String sourceFileName = string(body.get("sourceFileName")).trim();
        String content = normalizeTemplateContent(type, string(body.get("content")));
        if (name.isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }

        BusinessDocumentTemplate template = new BusinessDocumentTemplate();
        template.setCompanyId(companyId);
        template.setDocumentType(type);
        template.setName(name);
        template.setContent(content);
        template.setSourceFileName(sourceFileName);
        template.setCreatedBy(AuthContext.userId());
        templateMapper.insert(template);
        auditLogService.log(companyId, "BUSINESS_DOCUMENT_TEMPLATE", template.getId(),
                "CREATE", "上传" + typeLabel(type) + "模板 " + name);
        return templateView(templateMapper.selectById(template.getId()));
    }

    @Transactional
    public String deleteTemplate(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        int deleted = templateMapper.delete(new LambdaQueryWrapper<BusinessDocumentTemplate>()
                .eq(BusinessDocumentTemplate::getId, id)
                .eq(BusinessDocumentTemplate::getCompanyId, companyId));
        if (deleted == 0) {
            throw new BusinessException("单据模板不存在");
        }
        auditLogService.log(companyId, "BUSINESS_DOCUMENT_TEMPLATE", id, "DELETE", "删除单据模板");
        return "已删除";
    }

    public List<Map<String, Object>> listDocuments(Long contractId, String type) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        requireContractParty(contractId, companyId);
        String normalizedType = normalizeType(type);
        return documentMapper.selectList(new LambdaQueryWrapper<BusinessDocument>()
                        .eq(BusinessDocument::getCompanyId, companyId)
                        .eq(BusinessDocument::getContractId, contractId)
                        .eq(BusinessDocument::getDocumentType, normalizedType)
                        .orderByDesc(BusinessDocument::getCreatedAt)
                        .orderByDesc(BusinessDocument::getId))
                .stream().map(this::documentView).toList();
    }

    @Transactional
    public Map<String, Object> createDocument(Long contractId, Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_sign", "order_create");
        String type = normalizeType(string(body.get("documentType")));
        Long templateId = longValue(body.get("templateId"));
        if (templateId == null) {
            throw new BusinessException("请选择单据模板");
        }
        TradeContract contract = requireContractParty(contractId, companyId);
        BusinessDocumentTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<BusinessDocumentTemplate>()
                        .eq(BusinessDocumentTemplate::getId, templateId)
                        .eq(BusinessDocumentTemplate::getCompanyId, companyId)
                        .eq(BusinessDocumentTemplate::getDocumentType, type)
                        .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException("所选模板不存在或类型不匹配");
        }

        Company company = companyMapper.selectById(companyId);
        BusinessDocument document = new BusinessDocument();
        document.setCompanyId(companyId);
        document.setContractId(contractId);
        document.setDocumentType(type);
        document.setDocumentNo(createDocumentNo(type));
        document.setTemplateId(template.getId());
        document.setTemplateName(template.getName());
        document.setContent(createSnapshot(type, template, contract, company));
        document.setCreatedBy(AuthContext.userId());
        documentMapper.insert(document);
        auditLogService.log(companyId, "BUSINESS_DOCUMENT", document.getId(), "CREATE",
                "按模板 " + template.getName() + " 生成" + typeLabel(type));
        return documentView(documentMapper.selectById(document.getId()));
    }

    public BusinessDocument getDocument(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        BusinessDocument document = documentMapper.selectOne(new LambdaQueryWrapper<BusinessDocument>()
                .eq(BusinessDocument::getId, id)
                .eq(BusinessDocument::getCompanyId, companyId)
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException("单据不存在");
        }
        requireContractParty(document.getContractId(), companyId);
        return document;
    }

    public String typeLabel(String type) {
        return SALES_ORDER.equals(type) ? "销售单" : "送货单";
    }

    public String defaultTemplateContent(String type) {
        String normalizedType = normalizeType(type);
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode columns = content.putArray("columns");
        List<String> defaults = SALES_ORDER.equals(normalizedType)
                ? List.of("序号", "品名", "规格", "单位", "数量", "单价", "金额", "备注")
                : List.of("序号", "品名", "规格", "数量", "单位", "备注");
        defaults.forEach(columns::add);
        content.put("blankRows", SALES_ORDER.equals(normalizedType) ? 8 : 10);
        return content.toString();
    }

    private String normalizeTemplateContent(String type, String content) {
        if (content == null || content.isBlank()) {
            return defaultTemplateContent(type);
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isObject() || !root.path("columns").isArray()
                    || root.path("columns").isEmpty()) {
                return defaultTemplateContent(type);
            }
            return root.toString();
        } catch (Exception ignored) {
            return defaultTemplateContent(type);
        }
    }

    private String createSnapshot(String type, BusinessDocumentTemplate template,
                                  TradeContract contract, Company company) {
        try {
            JsonNode templateContent = objectMapper.readTree(
                    normalizeTemplateContent(type, template.getContent()));
            List<String> targetColumns = new ArrayList<>();
            templateContent.path("columns").forEach(node -> targetColumns.add(node.asText("")));
            ProductSource source = extractProducts(contract.getTerms());

            ObjectNode snapshot = objectMapper.createObjectNode();
            snapshot.put("title", typeLabel(type));
            snapshot.put("companyName", company == null ? "本方企业" : company.getName());
            snapshot.put("counterpartyName", contract.getCounterpartyName());
            snapshot.put("contractNo", safe(contract.getContractNo()));
            snapshot.put("documentNo", "");
            snapshot.put("date", LocalDate.now().toString());
            snapshot.put("templateName", template.getName());
            snapshot.put("blankRows", Math.max(
                    SALES_ORDER.equals(type) ? 8 : 10,
                    templateContent.path("blankRows").asInt(0)));
            ArrayNode columns = snapshot.putArray("columns");
            targetColumns.forEach(columns::add);
            ArrayNode rows = snapshot.putArray("rows");
            for (int rowIndex = 0; rowIndex < source.rows().size(); rowIndex++) {
                List<String> row = source.rows().get(rowIndex);
                ArrayNode targetRow = rows.addArray();
                for (String targetColumn : targetColumns) {
                    targetRow.add(valueForTarget(targetColumn, rowIndex, source.columns(), row));
                }
            }
            snapshot.put("totalAmount", contract.getAmount() == null
                    ? "0" : contract.getAmount().stripTrailingZeros().toPlainString());
            return snapshot.toString();
        } catch (Exception exception) {
            throw new BusinessException("合同商品数据格式不正确，无法生成单据");
        }
    }

    private ProductSource extractProducts(String terms) {
        if (terms == null || terms.isBlank()) {
            return new ProductSource(List.of(), List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(terms);
            JsonNode sections = root.path("sections");
            if (sections.isArray()) {
                for (JsonNode section : sections) {
                    if ("table".equalsIgnoreCase(section.path("type").asText())) {
                        List<String> columns = new ArrayList<>();
                        section.path("columns").forEach(node -> columns.add(node.asText("")));
                        List<List<String>> rows = new ArrayList<>();
                        for (JsonNode rowNode : section.path("rows")) {
                            List<String> row = new ArrayList<>();
                            rowNode.forEach(node -> row.add(node.asText("")));
                            if (row.stream().anyMatch(value -> value != null && !value.isBlank()
                                    && !"0".equals(value.trim()) && !"0.00".equals(value.trim()))) {
                                rows.add(row);
                            }
                        }
                        return new ProductSource(columns, rows);
                    }
                }
            }
        } catch (Exception ignored) {
            // 历史纯文本合同没有商品表格，仍可生成空白标准单据。
        }
        return new ProductSource(List.of(), List.of());
    }

    private String valueForTarget(String target, int rowIndex,
                                  List<String> sourceColumns, List<String> sourceRow) {
        String normalized = target == null ? "" : target.trim();
        if (normalized.contains("序号")) {
            return String.valueOf(rowIndex + 1);
        }
        List<String> aliases;
        if (normalized.contains("品名") || normalized.equals("名称") || normalized.contains("产品")) {
            aliases = List.of("品名", "名称", "产品");
        } else if (normalized.contains("规格")) {
            aliases = List.of("规格", "型号");
        } else if (normalized.contains("单位")) {
            aliases = List.of("单位");
        } else if (normalized.contains("数量")) {
            aliases = List.of("数量");
        } else if (normalized.contains("单价")) {
            aliases = List.of("单价");
        } else if (normalized.contains("金额")) {
            aliases = List.of("金额");
        } else if (normalized.contains("备注")) {
            aliases = List.of("备注");
        } else {
            aliases = List.of(normalized);
        }
        for (int index = 0; index < sourceColumns.size(); index++) {
            String sourceColumn = sourceColumns.get(index);
            boolean matched = aliases.stream().anyMatch(sourceColumn::contains);
            if (matched && index < sourceRow.size()) {
                return safe(sourceRow.get(index));
            }
        }
        return "";
    }

    private TradeContract requireContractParty(Long contractId, long companyId) {
        TradeContract contract = contractMapper.selectById(contractId);
        if (contract == null
                || (!Long.valueOf(companyId).equals(contract.getCompanyId())
                && !Long.valueOf(companyId).equals(contract.getCounterpartyCompanyId()))) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    private Map<String, Object> templateView(BusinessDocumentTemplate template) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", template.getId());
        view.put("documentType", template.getDocumentType());
        view.put("typeLabel", typeLabel(template.getDocumentType()));
        view.put("name", template.getName());
        view.put("sourceFileName", safe(template.getSourceFileName()));
        view.put("content", template.getContent());
        view.put("createdAt", template.getCreatedAt());
        view.put("updatedAt", template.getUpdatedAt());
        return view;
    }

    private Map<String, Object> documentView(BusinessDocument document) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", document.getId());
        view.put("documentType", document.getDocumentType());
        view.put("typeLabel", typeLabel(document.getDocumentType()));
        view.put("documentNo", document.getDocumentNo());
        view.put("templateId", document.getTemplateId());
        view.put("templateName", document.getTemplateName());
        view.put("createdAt", document.getCreatedAt());
        return view;
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!SALES_ORDER.equals(normalized) && !DELIVERY_NOTE.equals(normalized)) {
            throw new BusinessException("单据类型不正确");
        }
        return normalized;
    }

    private String createDocumentNo(String type) {
        String prefix = SALES_ORDER.equals(type) ? "XS" : "SH";
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return prefix + "-" + date + "-" + suffix;
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException("模板 ID 格式不正确");
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ProductSource(List<String> columns, List<List<String>> rows) {
    }
}
