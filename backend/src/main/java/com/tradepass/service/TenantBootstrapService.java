package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.BusinessDocumentTemplate;
import com.tradepass.entity.ContractTemplate;
import com.tradepass.entity.RoleDef;
import com.tradepass.entity.TemplateCategory;
import com.tradepass.mapper.BusinessDocumentTemplateMapper;
import com.tradepass.mapper.ContractTemplateMapper;
import com.tradepass.mapper.RoleDefMapper;
import com.tradepass.mapper.TemplateCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TenantBootstrapService {
    private static final Map<String, SeedRole> STANDARD_ROLES = standardRoles();
    private static final String STANDARD_CONTRACT_TEMPLATE = """
            {"title":"购销合同","fields":[
              {"key":"supplier","label":"供方","editable":false,"value":""},
              {"key":"buyer","label":"需方","editable":false,"value":""},
              {"key":"signDate","label":"签订日期","type":"date","editable":true,"value":""}
            ],"sections":[
              {"title":"商品明细","type":"table","columns":["品名","规格","单位","数量","单价","金额"],"rows":[]},
              {"title":"质量与验收","type":"clause","content":"双方按合同约定的质量标准和验收方式执行。"},
              {"title":"争议解决","type":"clause","content":"争议由双方协商解决；协商不成时依法处理。"}
            ]}
            """;

    private final RoleDefMapper roleDefMapper;
    private final TemplateCategoryMapper templateCategoryMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final BusinessDocumentTemplateMapper businessDocumentTemplateMapper;
    private final ObjectMapper objectMapper;

    public TenantBootstrapService(RoleDefMapper roleDefMapper,
                                  TemplateCategoryMapper templateCategoryMapper,
                                  ContractTemplateMapper contractTemplateMapper,
                                  BusinessDocumentTemplateMapper businessDocumentTemplateMapper,
                                  ObjectMapper objectMapper) {
        this.roleDefMapper = roleDefMapper;
        this.templateCategoryMapper = templateCategoryMapper;
        this.contractTemplateMapper = contractTemplateMapper;
        this.businessDocumentTemplateMapper = businessDocumentTemplateMapper;
        this.objectMapper = objectMapper;
    }

    public void initialize(long companyId, long operatorUserId) {
        STANDARD_ROLES.forEach((code, seed) -> seedRole(companyId, code, seed));
        seedCategory(companyId, "采购", 1);
        seedCategory(companyId, "供货", 2);
        seedCategory(companyId, "交易", 3);
        seedCategory(companyId, "服务", 4);
        seedContractTemplate(companyId, operatorUserId);
        seedDocumentTemplate(companyId, operatorUserId, "SALES_ORDER", "标准销售单模板",
                "{\"columns\":[\"序号\",\"品名\",\"规格\",\"单位\",\"数量\",\"单价\",\"金额\",\"备注\"],\"blankRows\":8}");
        seedDocumentTemplate(companyId, operatorUserId, "DELIVERY_NOTE", "标准送货单模板",
                "{\"columns\":[\"序号\",\"品名\",\"规格\",\"数量\",\"单位\",\"备注\"],\"blankRows\":10}");
    }

    public Map<String, SeedRole> standardRolesView() {
        return STANDARD_ROLES;
    }

    private void seedRole(long companyId, String code, SeedRole seed) {
        RoleDef existing = roleDefMapper.selectOne(new LambdaQueryWrapper<RoleDef>()
                .eq(RoleDef::getCompanyId, companyId)
                .eq(RoleDef::getCode, code)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        RoleDef role = new RoleDef();
        role.setCompanyId(companyId);
        role.setCode(code);
        role.setName(seed.name());
        role.setSystemRole(true);
        role.setPermissions(toJson(seed.permissions()));
        roleDefMapper.insert(role);
    }

    private void seedCategory(long companyId, String name, int sortOrder) {
        if (templateCategoryMapper.selectCount(new LambdaQueryWrapper<TemplateCategory>()
                .eq(TemplateCategory::getCompanyId, companyId)
                .eq(TemplateCategory::getName, name)) > 0) {
            return;
        }
        TemplateCategory category = new TemplateCategory();
        category.setCompanyId(companyId);
        category.setName(name);
        category.setSortOrder(sortOrder);
        templateCategoryMapper.insert(category);
    }

    private void seedContractTemplate(long companyId, long operatorUserId) {
        String name = "标准购销合同模板";
        if (contractTemplateMapper.selectCount(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getCompanyId, companyId)
                .eq(ContractTemplate::getName, name)) > 0) {
            return;
        }
        ContractTemplate template = new ContractTemplate();
        template.setCompanyId(companyId);
        template.setName(name);
        template.setCategory("交易");
        template.setContent(STANDARD_CONTRACT_TEMPLATE);
        template.setCreatedBy(operatorUserId);
        template.setUpdatedBy(operatorUserId);
        contractTemplateMapper.insert(template);
    }

    private void seedDocumentTemplate(long companyId, long operatorUserId, String type, String name, String content) {
        if (businessDocumentTemplateMapper.selectCount(new LambdaQueryWrapper<BusinessDocumentTemplate>()
                .eq(BusinessDocumentTemplate::getCompanyId, companyId)
                .eq(BusinessDocumentTemplate::getDocumentType, type)
                .eq(BusinessDocumentTemplate::getName, name)) > 0) {
            return;
        }
        BusinessDocumentTemplate template = new BusinessDocumentTemplate();
        template.setCompanyId(companyId);
        template.setDocumentType(type);
        template.setName(name);
        template.setContent(content);
        template.setCreatedBy(operatorUserId);
        businessDocumentTemplateMapper.insert(template);
    }

    private String toJson(List<String> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception e) {
            throw new BusinessException("标准角色初始化失败");
        }
    }

    private static Map<String, SeedRole> standardRoles() {
        Map<String, SeedRole> roles = new LinkedHashMap<>();
        roles.put("LEGAL", new SeedRole("法人", List.of("all")));
        roles.put("ADMIN", new SeedRole("管理员", List.of("member_manage", "auth_manage", "company_manage", "seal_manage", "contract_template")));
        roles.put("SALES", new SeedRole("销售员", List.of("supplier_view", "counterparty_manage", "order_view", "order_create", "contract_sign", "contract_view", "reconciliation")));
        roles.put("PURCHASER", new SeedRole("采购员", List.of("buyer_view", "order_create", "contract_view", "order_view", "contract_sign", "reconciliation")));
        roles.put("FINANCE", new SeedRole("财务", List.of("invoice_view", "reconciliation")));
        return Map.copyOf(roles);
    }

    public record SeedRole(String name, List<String> permissions) {
    }
}
