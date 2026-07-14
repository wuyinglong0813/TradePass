package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.common.TradePassDtos.CounterpartyRelation;
import com.tradepass.dto.request.AddCounterpartyRequest;
import com.tradepass.dto.request.CreateContractRequest;
import com.tradepass.dto.request.CreateOrderRequest;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.dto.response.TradeOrderPayload;
import com.tradepass.entity.Company;
import com.tradepass.entity.ContractTemplate;
import com.tradepass.entity.CounterpartyRelationEntity;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class TradeService {
    private final TradeOrderMapper tradeOrderMapper;
    private final CounterpartyRelationMapper counterpartyRelationMapper;
    private final TemplateCategoryMapper templateCategoryMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final TradeContractMapper tradeContractMapper;
    private final CompanyMapper companyMapper;
    private final AccessControlService accessControlService;

    public TradeService(TradeOrderMapper tradeOrderMapper,
                        CounterpartyRelationMapper counterpartyRelationMapper,
                        TemplateCategoryMapper templateCategoryMapper,
                        ContractTemplateMapper contractTemplateMapper,
                        TradeContractMapper tradeContractMapper,
                        CompanyMapper companyMapper,
                        AccessControlService accessControlService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.counterpartyRelationMapper = counterpartyRelationMapper;
        this.templateCategoryMapper = templateCategoryMapper;
        this.contractTemplateMapper = contractTemplateMapper;
        this.tradeContractMapper = tradeContractMapper;
        this.companyMapper = companyMapper;
        this.accessControlService = accessControlService;
    }

    public List<TradeOrderPayload> listOrders(String counterpartyName) {
        long companyId = AuthContext.requireCompanyId();
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getCompanyId, companyId)
                .orderByDesc(TradeOrder::getOrderDate);
        if (counterpartyName != null && !counterpartyName.isBlank()) {
            query.eq(TradeOrder::getCounterpartyName, counterpartyName);
        }
        return tradeOrderMapper.selectList(query).stream().map(this::toOrderPayload).toList();
    }

    public TradeOrderPayload createOrder(CreateOrderRequest request) {
        TradeOrder order = new TradeOrder();
        order.setCompanyId(AuthContext.requireCompanyId());
        order.setDirection(request.direction());
        order.setCounterpartyName(request.counterpartyName());
        order.setAmount(request.amount());
        order.setOrderDate(request.orderDate());
        order.setStatus("CONFIRMED");
        tradeOrderMapper.insert(order);
        return toOrderPayload(order);
    }

    public List<CounterpartyRelation> listCounterparties(String companyId) {
        long cid = accessControlService.resolveCompanyId(companyId);
        return counterpartyRelationMapper.selectList(new LambdaQueryWrapper<CounterpartyRelationEntity>()
                        .eq(CounterpartyRelationEntity::getCompanyId, cid)
                        .eq(CounterpartyRelationEntity::getStatus, "ACTIVE")
                        .orderByAsc(CounterpartyRelationEntity::getId))
                .stream()
                .map(row -> new CounterpartyRelation(String.valueOf(row.getId()), row.getCounterpartyCompanyName(), row.getRelationType(), row.getStatus()))
                .toList();
    }

    public CounterpartyRelation addCounterparty(AddCounterpartyRequest request) {
        long companyId = AuthContext.requireCompanyId();
        accessControlService.requireLegal(companyId);
        CounterpartyRelationEntity relation = new CounterpartyRelationEntity();
        relation.setCompanyId(companyId);
        relation.setCounterpartyCompanyName(request.counterpartyName());
        relation.setRelationType("SUPPLIER");
        relation.setStatus("ACTIVE");
        counterpartyRelationMapper.insert(relation);
        return new CounterpartyRelation(String.valueOf(relation.getId()), request.counterpartyName(), "SUPPLIER", "ACTIVE");
    }

    public List<Map<String, Object>> listTemplateCategories() {
        long companyId = AuthContext.requireCompanyId();
        return templateCategoryMapper.selectMaps(new LambdaQueryWrapper<TemplateCategory>()
                .select(TemplateCategory::getId, TemplateCategory::getName)
                .eq(TemplateCategory::getCompanyId, companyId)
                .orderByAsc(TemplateCategory::getSortOrder));
    }

    public Map<String, Object> addCategory(Map<String, Object> body) {
        long companyId = AuthContext.requireCompanyId();
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
        templateCategoryMapper.deleteById(id);
        return "已删除";
    }

    public List<Map<String, Object>> listTemplates(String keyword, String category) {
        return contractTemplateMapper.selectTemplateViews(AuthContext.requireCompanyId(), trim(keyword), trim(category));
    }

    public Map<String, Object> getTemplate(Long id) {
        Map<String, Object> template = contractTemplateMapper.selectTemplateView(id);
        if (template == null || template.isEmpty()) {
            throw new BusinessException("模板不存在");
        }
        return template;
    }

    public Map<String, Object> createTemplate(Map<String, Object> body) {
        String name = string(body.getOrDefault("name", ""));
        String category = string(body.getOrDefault("category", ""));
        String content = string(body.getOrDefault("content", ""));
        if (name.isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        ContractTemplate template = new ContractTemplate();
        template.setCompanyId(AuthContext.requireCompanyId());
        template.setName(name);
        template.setCategory(category);
        template.setContent(content);
        template.setCreatedBy(AuthContext.userId());
        contractTemplateMapper.insert(template);
        return contractTemplateMapper.selectMaps(new LambdaQueryWrapper<ContractTemplate>()
                .select(ContractTemplate::getId, ContractTemplate::getName, ContractTemplate::getCategory,
                        ContractTemplate::getContent, ContractTemplate::getCreatedBy, ContractTemplate::getUpdatedBy,
                        ContractTemplate::getCreatedAt, ContractTemplate::getUpdatedAt)
                .eq(ContractTemplate::getId, template.getId())).get(0);
    }

    public Map<String, Object> updateTemplate(Long id, Map<String, Object> body) {
        String name = string(body.getOrDefault("name", ""));
        String category = string(body.getOrDefault("category", ""));
        String content = string(body.getOrDefault("content", ""));
        if (name.isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        contractTemplateMapper.update(new LambdaUpdateWrapper<ContractTemplate>()
                .eq(ContractTemplate::getId, id)
                .set(ContractTemplate::getName, name)
                .set(ContractTemplate::getCategory, category)
                .set(ContractTemplate::getContent, content)
                .set(ContractTemplate::getUpdatedBy, AuthContext.userId()));
        return contractTemplateMapper.selectMaps(new LambdaQueryWrapper<ContractTemplate>()
                .select(ContractTemplate::getId, ContractTemplate::getName, ContractTemplate::getCategory,
                        ContractTemplate::getContent, ContractTemplate::getCreatedBy, ContractTemplate::getUpdatedBy,
                        ContractTemplate::getCreatedAt, ContractTemplate::getUpdatedAt)
                .eq(ContractTemplate::getId, id)).get(0);
    }

    public String deleteTemplate(Long id) {
        contractTemplateMapper.deleteById(id);
        return "已删除";
    }

    public ContractPayload getContract(Long id) {
        TradeContract contract = tradeContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        return toContractPayload(contract);
    }

    public List<ContractPayload> listContracts(String counterpartyName) {
        long companyId = AuthContext.requireCompanyId();
        LambdaQueryWrapper<TradeContract> query = new LambdaQueryWrapper<TradeContract>()
                .eq(TradeContract::getCompanyId, companyId)
                .orderByDesc(TradeContract::getCreatedAt);
        if (counterpartyName != null && !counterpartyName.isBlank()) {
            query.eq(TradeContract::getCounterpartyName, counterpartyName);
        }
        return tradeContractMapper.selectList(query).stream().map(this::toContractPayload).toList();
    }

    @Transactional
    public ContractPayload createContract(CreateContractRequest request) {
        TradeContract contract = new TradeContract();
        contract.setCompanyId(AuthContext.requireCompanyId());
        contract.setCounterpartyName(request.counterpartyName());
        contract.setName(request.name());
        contract.setTemplateName(request.templateName());
        contract.setAmount(request.amount());
        contract.setStartDate(parseDate(request.startDate()));
        contract.setEndDate(parseDate(request.endDate()));
        contract.setTerms(request.terms());
        contract.setStatus("PENDING");
        contract.setInitiatedBy(AuthContext.userId());
        tradeContractMapper.insert(contract);
        return toContractPayload(contract);
    }

    public String approveContract(Long id) {
        ensurePendingContract(id);
        tradeContractMapper.update(new LambdaUpdateWrapper<TradeContract>()
                .eq(TradeContract::getId, id)
                .set(TradeContract::getStatus, "ACTIVE"));
        return "合同已签署生效";
    }

    public String rejectContract(Long id) {
        ensurePendingContract(id);
        tradeContractMapper.update(new LambdaUpdateWrapper<TradeContract>()
                .eq(TradeContract::getId, id)
                .set(TradeContract::getStatus, "REJECTED"));
        return "合同已拒绝";
    }

    public List<ContractPayload> myInitiatedContracts() {
        return tradeContractMapper.selectList(new LambdaQueryWrapper<TradeContract>()
                        .eq(TradeContract::getInitiatedBy, AuthContext.userId())
                        .orderByDesc(TradeContract::getCreatedAt))
                .stream().map(this::toContractPayload).toList();
    }

    public List<ContractPayload> pendingContracts() {
        Long companyId = AuthContext.companyId();
        if (companyId == null) {
            return List.of();
        }
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            return List.of();
        }
        return tradeContractMapper.selectList(new LambdaQueryWrapper<TradeContract>()
                        .eq(TradeContract::getCounterpartyName, company.getName())
                        .eq(TradeContract::getStatus, "PENDING")
                        .orderByDesc(TradeContract::getCreatedAt))
                .stream().map(this::toContractPayload).toList();
    }

    private void ensurePendingContract(Long id) {
        Long count = tradeContractMapper.selectCount(new LambdaQueryWrapper<TradeContract>()
                .eq(TradeContract::getId, id)
                .eq(TradeContract::getStatus, "PENDING"));
        if (count == 0) {
            throw new BusinessException("合同不存在或状态不是待审批");
        }
    }

    private TradeOrderPayload toOrderPayload(TradeOrder order) {
        return new TradeOrderPayload(String.valueOf(order.getId()), order.getDirection(), order.getCounterpartyName(),
                order.getAmount(), order.getOrderDate(), order.getStatus());
    }

    private ContractPayload toContractPayload(TradeContract contract) {
        return new ContractPayload(
                String.valueOf(contract.getId()),
                contract.getCounterpartyName(),
                contract.getName(),
                contract.getTemplateName(),
                contract.getAmount(),
                contract.getStartDate() == null ? null : contract.getStartDate().toString(),
                contract.getEndDate() == null ? null : contract.getEndDate().toString(),
                contract.getTerms(),
                contract.getStatus(),
                String.valueOf(contract.getInitiatedBy()),
                contract.getCreatedAt() == null ? LocalDate.now().toString() : contract.getCreatedAt().toString()
        );
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
