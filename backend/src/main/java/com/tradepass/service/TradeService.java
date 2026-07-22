package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import com.tradepass.dto.request.AddCounterpartyRequest;
import com.tradepass.dto.request.CreateContractRequest;
import com.tradepass.dto.request.CreateOrderRequest;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.dto.response.PagePayload;
import com.tradepass.dto.response.TradeOrderPayload;
import com.tradepass.entity.Company;
import com.tradepass.entity.ContractTemplate;
import com.tradepass.entity.TemplateCategory;
import com.tradepass.entity.TradeContract;
import com.tradepass.entity.TradeOrder;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.ContractTemplateMapper;
import com.tradepass.mapper.CounterpartyRelationMapper;
import com.tradepass.mapper.TemplateCategoryMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.mapper.TradeOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TradeService {
    private final TradeOrderMapper tradeOrderMapper;
    private final CounterpartyRelationMapper counterpartyRelationMapper;
    private final TemplateCategoryMapper templateCategoryMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final TradeContractMapper tradeContractMapper;
    private final CompanyMapper companyMapper;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TradeService(TradeOrderMapper tradeOrderMapper,
                        CounterpartyRelationMapper counterpartyRelationMapper,
                        TemplateCategoryMapper templateCategoryMapper,
                        ContractTemplateMapper contractTemplateMapper,
                        TradeContractMapper tradeContractMapper,
                        CompanyMapper companyMapper,
                        AccessControlService accessControlService,
                        AuditLogService auditLogService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.counterpartyRelationMapper = counterpartyRelationMapper;
        this.templateCategoryMapper = templateCategoryMapper;
        this.contractTemplateMapper = contractTemplateMapper;
        this.tradeContractMapper = tradeContractMapper;
        this.companyMapper = companyMapper;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
    }

    public List<TradeOrderPayload> listOrders(String counterpartyName) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "order_view", "reconciliation");
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getCompanyId, companyId)
                .orderByDesc(TradeOrder::getOrderDate);
        if (counterpartyName != null && !counterpartyName.isBlank()) {
            query.eq(TradeOrder::getCounterpartyName, counterpartyName);
        }
        return tradeOrderMapper.selectList(query).stream().map(this::toOrderPayload).toList();
    }

    public PagePayload<TradeOrderPayload> pageOrders(String counterpartyName, String direction, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "order_view", "reconciliation");
        long total = tradeOrderMapper.selectCount(orderQuery(companyId, counterpartyName, direction));
        List<TradeOrderPayload> items = tradeOrderMapper.selectList(orderQuery(companyId, counterpartyName, direction)
                        .last(limitClause(normalizedPage, normalizedSize)))
                .stream().map(this::toOrderPayload).toList();
        return PagePayload.of(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> orderSummary(String counterpartyName, String direction) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "order_view", "reconciliation");
        validateDirection(direction, false);
        return tradeOrderMapper.selectOrderSummary(companyId, trim(counterpartyName), trim(direction));
    }

    public List<Map<String, Object>> monthlyOrderSummary(String counterpartyName, String direction) {
        String cleanName = trim(counterpartyName);
        String cleanDirection = trim(direction);
        if (cleanName == null || cleanName.isBlank() || cleanDirection == null || cleanDirection.isBlank()) {
            throw new BusinessException("合作方和交易方向不能为空");
        }
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "order_view", "reconciliation");
        validateDirection(cleanDirection, true);
        return tradeOrderMapper.selectMonthlyOrderSummary(
                companyId, cleanName, cleanDirection);
    }

    @Transactional
    public TradeOrderPayload createOrder(CreateOrderRequest request) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "order_create");
        validateDirection(request.direction(), true);
        if (request.clientRequestId() != null && !request.clientRequestId().isBlank()) {
            TradeOrder existing = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getCompanyId, companyId)
                    .eq(TradeOrder::getClientRequestId, request.clientRequestId().trim())
                    .last("LIMIT 1"));
            if (existing != null) {
                return toOrderPayload(existing);
            }
        }
        BigDecimal amount = requireNonNegativeAmount(request.amount(), "订单金额");
        TradeOrder order = new TradeOrder();
        order.setCompanyId(companyId);
        order.setCounterpartyCompanyId(request.counterpartyCompanyId());
        order.setDirection(request.direction());
        order.setCounterpartyName(request.counterpartyName().trim());
        order.setOrderNo(normalizeBusinessNo(request.orderNo(), "ORD"));
        order.setClientRequestId(trim(request.clientRequestId()));
        order.setAmount(amount);
        order.setOrderDate(request.orderDate());
        order.setStatus("CONFIRMED");
        order.setCreatedBy(AuthContext.userId());
        tradeOrderMapper.insert(order);
        auditLogService.log(companyId, "ORDER", order.getId(), "CREATE",
                "创建订单 " + order.getOrderNo() + "，金额 " + amount);
        return toOrderPayload(order);
    }

    public List<CounterpartyRelation> listCounterparties(String companyId, String role) {
        long cid = accessControlService.resolveCompanyId(companyId);
        if ("supplier".equalsIgnoreCase(role)) {
            accessControlService.requireAnyPermission(cid, "supplier_view", "counterparty_manage", "contract_sign");
        } else {
            accessControlService.requireAnyPermission(cid, "buyer_view", "order_create", "contract_sign");
        }
        List<Map<String, Object>> rows = "supplier".equalsIgnoreCase(role)
                ? counterpartyRelationMapper.selectSupplierCounterparties(cid)
                : counterpartyRelationMapper.selectBuyerCounterparties(cid);
        return rows.stream()
                .filter(row -> row.get("counterpartyCompanyId") != null)
                .map(row -> new CounterpartyRelation(
                        String.valueOf(row.get("id")),
                        string(row.get("counterpartyCompanyId")),
                        string(row.get("counterpartyName")),
                        string(row.get("relationType")),
                        string(row.get("status"))))
                .toList();
    }

    public CounterpartyRelation addCounterparty(AddCounterpartyRequest request) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireLegal(companyId);
        throw new BusinessException("请使用合作企业邀请，对方接受后才会建立有效关系");
    }

    public List<Map<String, Object>> listTemplateCategories() {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_template", "contract_sign");
        return templateCategoryMapper.selectMaps(new LambdaQueryWrapper<TemplateCategory>()
                .select(TemplateCategory::getId, TemplateCategory::getName)
                .eq(TemplateCategory::getCompanyId, companyId)
                .orderByAsc(TemplateCategory::getSortOrder));
    }

    public Map<String, Object> addCategory(Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        String name = ((String) body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            throw new BusinessException("分类名不能为空");
        }
        TemplateCategory existing = templateCategoryMapper.selectOne(new LambdaQueryWrapper<TemplateCategory>()
                .eq(TemplateCategory::getCompanyId, companyId)
                .eq(TemplateCategory::getName, name)
                .last("LIMIT 1"));
        if (existing == null) {
            TemplateCategory category = new TemplateCategory();
            category.setCompanyId(companyId);
            category.setName(name);
            category.setSortOrder(0);
            templateCategoryMapper.insert(category);
        }
        return templateCategoryMapper.selectMaps(new LambdaQueryWrapper<TemplateCategory>()
                .select(TemplateCategory::getId, TemplateCategory::getName)
                .eq(TemplateCategory::getCompanyId, companyId)
                .eq(TemplateCategory::getName, name)
                .last("LIMIT 1")).get(0);
    }

    public String deleteCategory(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        templateCategoryMapper.delete(new LambdaQueryWrapper<TemplateCategory>()
                .eq(TemplateCategory::getId, id)
                .eq(TemplateCategory::getCompanyId, companyId));
        return "已删除";
    }

    public List<Map<String, Object>> listTemplates(String keyword, String category) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_template", "contract_sign");
        return contractTemplateMapper.selectTemplateViews(companyId, trim(keyword), trim(category));
    }

    public PagePayload<Map<String, Object>> pageTemplates(String keyword, String category, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_template", "contract_sign");
        String cleanKeyword = trim(keyword);
        String cleanCategory = trim(category);
        LambdaQueryWrapper<ContractTemplate> query = new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getCompanyId, companyId);
        if (cleanKeyword != null && !cleanKeyword.isBlank()) {
            query.like(ContractTemplate::getName, cleanKeyword);
        }
        if (cleanCategory != null && !cleanCategory.isBlank() && !"all".equalsIgnoreCase(cleanCategory)) {
            query.eq(ContractTemplate::getCategory, cleanCategory);
        }
        long total = contractTemplateMapper.selectCount(query);
        long offset = (long) (normalizedPage - 1) * normalizedSize;
        List<Map<String, Object>> items = contractTemplateMapper.selectTemplatePageViews(
                companyId, cleanKeyword, cleanCategory, normalizedSize, offset);
        return PagePayload.of(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> getTemplate(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_template", "contract_sign");
        Map<String, Object> template = contractTemplateMapper.selectTemplateView(id, companyId);
        if (template == null || template.isEmpty()) {
            throw new BusinessException("模板不存在");
        }
        return template;
    }

    public Map<String, Object> createTemplate(Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        String name = string(body.getOrDefault("name", ""));
        String category = string(body.getOrDefault("category", ""));
        String content = string(body.getOrDefault("content", ""));
        if (name.isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        ContractTemplate template = new ContractTemplate();
        template.setCompanyId(companyId);
        template.setName(name);
        template.setCategory(category);
        template.setContent(content);
        template.setCreatedBy(AuthContext.userId());
        contractTemplateMapper.insert(template);
        auditLogService.log(companyId, "CONTRACT_TEMPLATE", template.getId(), "CREATE", "创建模板 " + name);
        return contractTemplateMapper.selectMaps(new LambdaQueryWrapper<ContractTemplate>()
                .select(ContractTemplate::getId, ContractTemplate::getName, ContractTemplate::getCategory,
                        ContractTemplate::getContent, ContractTemplate::getCreatedBy, ContractTemplate::getUpdatedBy,
                        ContractTemplate::getCreatedAt, ContractTemplate::getUpdatedAt)
                .eq(ContractTemplate::getId, template.getId())).get(0);
    }

    public Map<String, Object> updateTemplate(Long id, Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        String name = string(body.getOrDefault("name", ""));
        String category = string(body.getOrDefault("category", ""));
        String content = string(body.getOrDefault("content", ""));
        if (name.isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        contractTemplateMapper.update(new LambdaUpdateWrapper<ContractTemplate>()
                .eq(ContractTemplate::getId, id)
                .eq(ContractTemplate::getCompanyId, companyId)
                .set(ContractTemplate::getName, name)
                .set(ContractTemplate::getCategory, category)
                .set(ContractTemplate::getContent, content)
                .set(ContractTemplate::getUpdatedBy, AuthContext.userId()));
        List<Map<String, Object>> updated = contractTemplateMapper.selectMaps(new LambdaQueryWrapper<ContractTemplate>()
                .select(ContractTemplate::getId, ContractTemplate::getName, ContractTemplate::getCategory,
                        ContractTemplate::getContent, ContractTemplate::getCreatedBy, ContractTemplate::getUpdatedBy,
                        ContractTemplate::getCreatedAt, ContractTemplate::getUpdatedAt)
                .eq(ContractTemplate::getId, id)
                .eq(ContractTemplate::getCompanyId, companyId));
        if (updated.isEmpty()) {
            throw new BusinessException("模板不存在");
        }
        auditLogService.log(companyId, "CONTRACT_TEMPLATE", id, "UPDATE", "更新模板 " + name);
        return updated.get(0);
    }

    public String deleteTemplate(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_template");
        int deleted = contractTemplateMapper.delete(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getId, id)
                .eq(ContractTemplate::getCompanyId, companyId));
        if (deleted == 0) {
            throw new BusinessException("模板不存在");
        }
        auditLogService.log(companyId, "CONTRACT_TEMPLATE", id, "DELETE", "删除合同模板");
        return "已删除";
    }

    public ContractPayload getContract(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        TradeContract contract = tradeContractMapper.selectById(id);
        if (contract == null || !isContractParty(contract, companyId)) {
            throw new BusinessException("合同不存在");
        }
        return toContractPayload(contract, companyId);
    }

    public List<ContractPayload> listContracts(String counterpartyName) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        return tradeContractMapper.selectPartyContracts(companyId, trim(counterpartyName), null, 1000, 0)
                .stream().map(contract -> toContractPayload(contract, companyId)).toList();
    }

    public PagePayload<ContractPayload> pageContracts(String counterpartyName, String status, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        String cleanName = trim(counterpartyName);
        String cleanStatus = trim(status);
        long total = tradeContractMapper.countPartyContracts(companyId, cleanName, cleanStatus);
        long offset = (long) (normalizedPage - 1) * normalizedSize;
        List<ContractPayload> items = tradeContractMapper.selectPartyContracts(
                        companyId, cleanName, cleanStatus, normalizedSize, offset)
                .stream().map(contract -> toContractPayload(contract, companyId)).toList();
        return PagePayload.of(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> contractSummary() {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        return tradeContractMapper.selectContractSummary(companyId);
    }

    @Transactional
    public ContractPayload createContract(CreateContractRequest request) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_sign");
        validateDirection(request.direction(), true);
        LocalDate startDate = parseDate(request.startDate());
        LocalDate endDate = parseDate(request.endDate());
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("合同结束日期不能早于开始日期");
        }
        if (request.clientRequestId() != null && !request.clientRequestId().isBlank()) {
            TradeContract existing = tradeContractMapper.selectOne(new LambdaQueryWrapper<TradeContract>()
                    .eq(TradeContract::getCompanyId, companyId)
                    .eq(TradeContract::getClientRequestId, request.clientRequestId().trim())
                    .last("LIMIT 1"));
            if (existing != null) {
                return toContractPayload(existing, companyId);
            }
        }

        Company counterparty = requireActiveCounterparty(companyId, request.counterpartyCompanyId());
        if (counterparty.getId() == companyId) {
            throw new BusinessException("合同对方不能是当前企业");
        }
        Company initiator = companyMapper.selectById(companyId);
        if (initiator == null) {
            throw new BusinessException("当前企业不存在");
        }
        String direction = normalizeDirection(request.direction());
        String contractName = request.name().trim();
        BigDecimal amount = requireNonNegativeAmount(request.amount(), "合同金额");
        TradeContract contract = new TradeContract();
        contract.setCompanyId(companyId);
        contract.setContractNo(normalizeBusinessNo(request.contractNo(), "HT"));
        contract.setCounterpartyCompanyId(counterparty.getId());
        contract.setCounterpartyName(counterparty.getName());
        contract.setDirection(direction);
        contract.setClientRequestId(trim(request.clientRequestId()));
        contract.setName(contractName);
        contract.setTemplateName(request.templateName());
        contract.setAmount(amount);
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setTerms(normalizeContractTerms(request.terms(), contractName,
                initiator.getName(), counterparty.getName(), direction));
        contract.setVersionNo(1);
        contract.setStatus("PENDING");
        contract.setInitiatedBy(AuthContext.userId());
        try {
            tradeContractMapper.insert(contract);
        } catch (DuplicateKeyException duplicate) {
            TradeContract existing = tradeContractMapper.selectOne(new LambdaQueryWrapper<TradeContract>()
                    .eq(TradeContract::getCompanyId, companyId)
                    .eq(TradeContract::getClientRequestId, request.clientRequestId().trim())
                    .last("LIMIT 1"));
            if (existing != null) {
                return toContractPayload(existing, companyId);
            }
            throw duplicate;
        }
        auditLogService.log(companyId, "CONTRACT", contract.getId(), "CREATE",
                "发起合同 " + contract.getContractNo() + "，金额 " + amount);
        return toContractPayload(contract, companyId);
    }

    @Transactional
    public String approveContract(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_sign");
        TradeContract contract = ensurePendingIncomingContract(id, companyId);
        tradeContractMapper.update(new LambdaUpdateWrapper<TradeContract>()
                .eq(TradeContract::getId, id)
                .eq(TradeContract::getStatus, "PENDING")
                .set(TradeContract::getStatus, "ACTIVE")
                .set(TradeContract::getApprovedBy, AuthContext.userId())
                .set(TradeContract::getApprovedAt, LocalDateTime.now()));
        auditLogService.log(companyId, "CONTRACT", id, "APPROVE",
                "签署合同 " + contract.getContractNo());
        return "合同已签署生效";
    }

    @Transactional
    public String rejectContract(Long id) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requirePermission(companyId, "contract_sign");
        TradeContract contract = ensurePendingIncomingContract(id, companyId);
        tradeContractMapper.update(new LambdaUpdateWrapper<TradeContract>()
                .eq(TradeContract::getId, id)
                .eq(TradeContract::getStatus, "PENDING")
                .set(TradeContract::getStatus, "REJECTED")
                .set(TradeContract::getApprovedBy, AuthContext.userId())
                .set(TradeContract::getApprovedAt, LocalDateTime.now()));
        auditLogService.log(companyId, "CONTRACT", id, "REJECT",
                "拒绝合同 " + contract.getContractNo());
        return "合同已拒绝";
    }

    public List<ContractPayload> myInitiatedContracts() {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireAnyPermission(companyId, "contract_view", "contract_sign");
        return tradeContractMapper.selectList(new LambdaQueryWrapper<TradeContract>()
                        .eq(TradeContract::getCompanyId, companyId)
                .eq(TradeContract::getInitiatedBy, AuthContext.userId())
                        .orderByDesc(TradeContract::getCreatedAt))
                .stream().map(contract -> toContractPayload(contract, companyId)).toList();
    }

    public List<ContractPayload> pendingContracts() {
        Long companyId = AuthContext.companyId();
        if (companyId == null) {
            return List.of();
        }
        accessControlService.requirePermission(companyId, "contract_sign");
        return tradeContractMapper.selectList(new LambdaQueryWrapper<TradeContract>()
                        .eq(TradeContract::getCounterpartyCompanyId, companyId)
                        .eq(TradeContract::getStatus, "PENDING")
                        .orderByDesc(TradeContract::getCreatedAt))
                .stream().map(contract -> toContractPayload(contract, companyId)).toList();
    }

    private TradeContract ensurePendingIncomingContract(Long id, long companyId) {
        TradeContract contract = tradeContractMapper.selectOne(new LambdaQueryWrapper<TradeContract>()
                .eq(TradeContract::getId, id)
                .eq(TradeContract::getCounterpartyCompanyId, companyId)
                .eq(TradeContract::getStatus, "PENDING")
                .last("LIMIT 1"));
        if (contract == null) {
            throw new BusinessException("合同不存在或状态不是待审批");
        }
        return contract;
    }

    private LambdaQueryWrapper<TradeOrder> orderQuery(long companyId, String counterpartyName, String direction) {
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getCompanyId, companyId)
                .orderByDesc(TradeOrder::getOrderDate);
        String cleanName = trim(counterpartyName);
        if (cleanName != null && !cleanName.isBlank()) {
            query.eq(TradeOrder::getCounterpartyName, cleanName);
        }
        String cleanDirection = trim(direction);
        if (cleanDirection != null && !cleanDirection.isBlank()) {
            query.eq(TradeOrder::getDirection, cleanDirection);
        }
        return query;
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private String limitClause(int page, int size) {
        long offset = (long) (page - 1) * size;
        return "LIMIT " + size + " OFFSET " + offset;
    }

    private TradeOrderPayload toOrderPayload(TradeOrder order) {
        return new TradeOrderPayload(
                idString(order.getId()),
                order.getDirection(),
                order.getCounterpartyName(),
                order.getOrderNo(),
                order.getAmount(),
                order.getOrderDate(),
                order.getStatus()
        );
    }

    private ContractPayload toContractPayload(TradeContract contract, long viewerCompanyId) {
        boolean outgoing = contract.getCompanyId() == viewerCompanyId;
        Long viewerCounterpartyId = outgoing ? contract.getCounterpartyCompanyId() : contract.getCompanyId();
        String viewerCounterpartyName = outgoing
                ? contract.getCounterpartyName()
                : companyName(contract.getCompanyId());
        String viewerDirection = outgoing
                ? normalizeDirection(contract.getDirection())
                : invertDirection(contract.getDirection());
        return new ContractPayload(
                idString(contract.getId()),
                contract.getContractNo(),
                idString(contract.getCompanyId()),
                idString(contract.getCounterpartyCompanyId()),
                contract.getCounterpartyName(),
                contract.getDirection(),
                contract.getName(),
                contract.getTemplateName(),
                contract.getAmount(),
                contract.getStartDate() == null ? null : contract.getStartDate().toString(),
                contract.getEndDate() == null ? null : contract.getEndDate().toString(),
                contract.getTerms(),
                contract.getStatus(),
                contract.getVersionNo(),
                idString(contract.getInitiatedBy()),
                idString(contract.getApprovedBy()),
                contract.getApprovedAt() == null ? null : contract.getApprovedAt().toString(),
                contract.getCreatedAt() == null ? LocalDate.now().toString() : contract.getCreatedAt().toString(),
                String.valueOf(viewerCompanyId),
                idString(viewerCounterpartyId),
                viewerCounterpartyName,
                viewerDirection,
                outgoing ? "OUTGOING" : "INCOMING"
        );
    }

    private BigDecimal requireNonNegativeAmount(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + "不能为负数");
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }
    private Company requireActiveCounterparty(long companyId, Long requestedId) {
        if (requestedId == null) {
            throw new BusinessException("请选择已建立合作关系的企业");
        }
        Company company = companyMapper.selectById(requestedId);
        if (company == null) {
            throw new BusinessException("合作企业不存在");
        }
        if (counterpartyRelationMapper.countActiveBetween(companyId, requestedId) <= 0) {
            throw new BusinessException("与该企业尚未建立有效合作关系");
        }
        return company;
    }

    private String normalizeContractTerms(String terms, String contractName, String initiatorName,
                                          String counterpartyName, String direction) {
        if (terms == null || terms.isBlank()) {
            return terms;
        }
        try {
            JsonNode parsed = objectMapper.readTree(terms);
            if (!(parsed instanceof ObjectNode root)) {
                throw new BusinessException("合同正文格式错误");
            }
            root.put("title", contractName);
            JsonNode fieldsNode = root.get("fields");
            if (fieldsNode instanceof ArrayNode fields) {
                String supplier = "SALE".equals(direction) ? initiatorName : counterpartyName;
                String buyer = "SALE".equals(direction) ? counterpartyName : initiatorName;
                for (JsonNode node : fields) {
                    if (node instanceof ObjectNode field) {
                        String key = field.path("key").asText();
                        if ("supplier".equals(key)) field.put("value", supplier);
                        if ("buyer".equals(key)) field.put("value", buyer);
                    }
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("合同正文格式错误");
        }
    }

    private String companyName(Long companyId) {
        Company company = companyId == null ? null : companyMapper.selectById(companyId);
        return company == null ? "未知企业" : company.getName();
    }

    private String invertDirection(String direction) {
        return "PURCHASE".equalsIgnoreCase(direction) ? "SALE" : "PURCHASE";
    }

    private boolean isContractParty(TradeContract contract, long companyId) {
        return contract.getCompanyId() == companyId
                || (contract.getCounterpartyCompanyId() != null && contract.getCounterpartyCompanyId() == companyId);
    }

    private void validateDirection(String direction, boolean required) {
        if (direction == null || direction.isBlank()) {
            if (required) {
                throw new BusinessException("交易方向不能为空");
            }
            return;
        }
        if (!"SALE".equalsIgnoreCase(direction) && !"PURCHASE".equalsIgnoreCase(direction)) {
            throw new BusinessException("交易方向只能是 SALE 或 PURCHASE");
        }
    }

    private String normalizeDirection(String direction) {
        return direction == null || direction.isBlank() ? "SALE" : direction.trim().toUpperCase();
    }

    private String normalizeBusinessNo(String requested, String prefix) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + "-" + day + "-" + suffix;
    }

    private String idString(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
