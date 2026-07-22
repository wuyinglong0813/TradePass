package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeServiceTest {
    private TradeOrderMapper orderMapper;
    private CounterpartyRelationMapper relationMapper;
    private TemplateCategoryMapper categoryMapper;
    private ContractTemplateMapper templateMapper;
    private TradeContractMapper contractMapper;
    private CompanyMapper companyMapper;
    private AccessControlService accessControl;
    private AuditLogService auditLogService;
    private TradeService service;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(
                ContractTemplate.class, TradeContract.class, TemplateCategory.class, Company.class,
                CounterpartyRelationEntity.class, TradeOrder.class
        );
        orderMapper = mock(TradeOrderMapper.class);
        relationMapper = mock(CounterpartyRelationMapper.class);
        categoryMapper = mock(TemplateCategoryMapper.class);
        templateMapper = mock(ContractTemplateMapper.class);
        contractMapper = mock(TradeContractMapper.class);
        companyMapper = mock(CompanyMapper.class);
        accessControl = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        service = new TradeService(orderMapper, relationMapper, categoryMapper, templateMapper,
                contractMapper, companyMapper, accessControl, auditLogService);
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void createsAndListsTenantScopedOrders() {
        doAnswer(invocation -> {
            TradeOrder order = invocation.getArgument(0);
            order.setId(12L);
            return 1;
        }).when(orderMapper).insert(any(TradeOrder.class));
        CreateOrderRequest request = new CreateOrderRequest("SALE", "合作方",
                new BigDecimal("88.50"), LocalDate.of(2026, 7, 1));

        TradeOrderPayload created = service.createOrder(request);

        assertThat(created.id()).isEqualTo("12");
        assertThat(created.status()).isEqualTo("CONFIRMED");
        assertThat(created.amount()).isEqualByComparingTo("88.50");

        TradeOrder order = new TradeOrder();
        order.setId(12L);
        order.setDirection("SALE");
        order.setCounterpartyName("合作方");
        order.setAmount(new BigDecimal("88.50"));
        order.setOrderDate(LocalDate.of(2026, 7, 1));
        order.setStatus("CONFIRMED");
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order));
        assertThat(service.listOrders(" 合作方 ")).extracting(TradeOrderPayload::id).containsExactly("12");
    }

    @Test
    void mapsBuyerAndSupplierCounterpartyViews() {
        when(accessControl.resolveCompanyId("3")).thenReturn(3L);
        when(relationMapper.selectBuyerCounterparties(3L)).thenReturn(List.of(
                Map.of("id", 5L, "counterpartyCompanyId", 9L, "counterpartyName", "供应方", "relationType", "SUPPLIER", "status", "ACTIVE"),
                Map.of("id", 7L, "counterpartyName", "历史名称关系", "relationType", "SUPPLIER", "status", "ACTIVE")
        ));

        List<CounterpartyRelation> buyerView = service.listCounterparties("3", "buyer");
        assertThat(buyerView).hasSize(1);
        assertThat(buyerView.get(0).counterpartyName()).isEqualTo("供应方");

        when(relationMapper.selectSupplierCounterparties(3L)).thenReturn(List.of(
                Map.of("id", 6L, "counterpartyCompanyId", 10L, "counterpartyName", "采购方", "relationType", "CUSTOMER", "status", "ACTIVE")
        ));
        List<CounterpartyRelation> supplierView = service.listCounterparties("3", "supplier");
        assertThat(supplierView.get(0).counterpartyName()).isEqualTo("采购方");
        assertThat(supplierView.get(0).relationType()).isEqualTo("CUSTOMER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesTenantOrdersContractsAndTemplates() {
        TradeOrder order = new TradeOrder();
        order.setId(12L);
        order.setDirection("SALE");
        order.setCounterpartyName("客户");
        order.setAmount(new BigDecimal("88.50"));
        order.setOrderDate(LocalDate.of(2026, 7, 1));
        order.setStatus("CONFIRMED");
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(25L);
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order));
        assertThat(service.pageOrders("客户", "SALE", 2, 10)).satisfies(page -> {
            assertThat(page.total()).isEqualTo(25);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.items()).extracting(TradeOrderPayload::id).containsExactly("12");
        });
        when(orderMapper.selectMonthlyOrderSummary(3L, "客户", "SALE"))
                .thenReturn(List.of(Map.of("period", "2026-07", "amount", new BigDecimal("88.50"))));
        assertThat(service.monthlyOrderSummary(" 客户 ", " SALE "))
                .extracting(item -> item.get("period")).containsExactly("2026-07");
        assertThatThrownBy(() -> service.monthlyOrderSummary("", "SALE"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("合作方和交易方向不能为空");

        TradeContract contract = new TradeContract();
        contract.setId(20L);
        contract.setCompanyId(3L);
        contract.setCounterpartyName("客户");
        contract.setName("采购合同");
        contract.setAmount(BigDecimal.TEN);
        contract.setStatus("PENDING");
        contract.setInitiatedBy(7L);
        when(contractMapper.countPartyContracts(3L, null, "PENDING")).thenReturn(1L);
        when(contractMapper.selectPartyContracts(3L, null, "PENDING", 20, 0L)).thenReturn(List.of(contract));
        assertThat(service.pageContracts(null, "PENDING", 1, 20).items())
                .extracting(ContractPayload::id).containsExactly("20");

        when(templateMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(templateMapper.selectTemplatePageViews(3L, "采购", "通用", 20, 0L))
                .thenReturn(List.of(Map.of("id", 2L, "name", "采购合同")));
        assertThat(service.pageTemplates(" 采购 ", "通用", 1, 20).items())
                .extracting(item -> item.get("name")).containsExactly("采购合同");
    }

    @Test
    void requiresInvitationWhenAddingCounterparty() {
        assertThatThrownBy(() -> service.addCounterparty(new AddCounterpartyRequest("新供应商")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合作企业邀请");
        verify(accessControl).requireLegal(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesAndCreatesTemplateCategory() {
        assertThatThrownBy(() -> service.addCategory(Map.of("name", "  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类名不能为空");

        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(categoryMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(Map.of("id", 4L, "name", "采购")));
        Map<String, Object> result = service.addCategory(Map.of("name", " 采购 "));

        assertThat(result).containsEntry("name", "采购");
        verify(categoryMapper).insert(any(com.tradepass.entity.TemplateCategory.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesCreatesAndUpdatesContractTemplates() {
        assertThatThrownBy(() -> service.createTemplate(Map.of("name", " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("模板名称不能为空");

        doAnswer(invocation -> {
            ContractTemplate template = invocation.getArgument(0);
            template.setId(9L);
            return 1;
        }).when(templateMapper).insert(any(ContractTemplate.class));
        Map<String, Object> view = Map.of("id", 9L, "name", "标准合同", "category", "采购");
        when(templateMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(view));

        assertThat(service.createTemplate(Map.of("name", "标准合同", "category", "采购", "content", "{}")))
                .containsEntry("id", 9L);
        assertThat(service.updateTemplate(9L, Map.of("name", "新版合同", "content", "{}")))
                .containsEntry("id", 9L);
        verify(templateMapper).update(any(Wrapper.class));
    }

    @Test
    void rejectsMissingTemplateAndContract() {
        when(templateMapper.selectTemplateView(99L, 3L)).thenReturn(Map.of());
        assertThatThrownBy(() -> service.getTemplate(99L))
                .isInstanceOf(BusinessException.class).hasMessage("模板不存在");

        when(contractMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getContract(99L))
                .isInstanceOf(BusinessException.class).hasMessage("合同不存在");
    }

    @Test
    void createsContractAndProtectsStatusTransitions() {
        doAnswer(invocation -> {
            TradeContract contract = invocation.getArgument(0);
            contract.setId(20L);
            return 1;
        }).when(contractMapper).insert(any(TradeContract.class));
        Company initiator = new Company();
        initiator.setId(3L);
        initiator.setName("当前企业");
        Company counterparty = new Company();
        counterparty.setId(9L);
        counterparty.setName("对方企业");
        when(companyMapper.selectById(3L)).thenReturn(initiator);
        when(companyMapper.selectById(9L)).thenReturn(counterparty);
        when(relationMapper.countActiveBetween(3L, 9L)).thenReturn(1L);
        CreateContractRequest request = new CreateContractRequest("用户输入名称", "年度合同", "模板",
                new BigDecimal("1000"), "2026-01-01", "2026-12-31", "{}",
                9L, "SALE", null, "request-20");

        ContractPayload created = service.createContract(request);
        assertThat(created.id()).isEqualTo("20");
        assertThat(created.startDate()).isEqualTo("2026-01-01");
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.initiatedBy()).isEqualTo("7");
        assertThat(created.counterpartyName()).isEqualTo("对方企业");
        assertThat(created.viewerDirection()).isEqualTo("SALE");
        assertThat(created.terms()).contains("\"title\":\"年度合同\"");

        TradeContract incoming = new TradeContract();
        incoming.setId(20L);
        incoming.setCompanyId(9L);
        incoming.setCounterpartyCompanyId(3L);
        incoming.setContractNo("HT-TEST");
        incoming.setStatus("PENDING");
        when(contractMapper.selectOne(any(Wrapper.class))).thenReturn(null, incoming, incoming);
        assertThatThrownBy(() -> service.approveContract(20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("合同不存在或状态不是待审批");
        assertThat(service.approveContract(20L)).isEqualTo("合同已签署生效");
        assertThat(service.rejectContract(20L)).isEqualTo("合同已拒绝");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingContractsStayScopedToCurrentCompany() {
        AuthContext.set(7L, null);
        assertThat(service.pendingContracts()).isEmpty();

        AuthContext.set(7L, 3L);
        assertThat(service.pendingContracts()).isEmpty();

        TradeContract pending = new TradeContract();
        pending.setId(1L);
        pending.setCompanyId(9L);
        pending.setCounterpartyCompanyId(3L);
        pending.setCounterpartyName("当前企业");
        pending.setName("待签合同");
        pending.setStatus("PENDING");
        pending.setInitiatedBy(8L);
        when(contractMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pending));

        assertThat(service.pendingContracts()).extracting(ContractPayload::id).containsExactly("1");
    }

    @Test
    void sharedLedgerUsesTheReceivingCompanyPerspective() {
        TradeContract incoming = new TradeContract();
        incoming.setId(31L);
        incoming.setCompanyId(9L);
        incoming.setCounterpartyCompanyId(3L);
        incoming.setCounterpartyName("当前企业");
        incoming.setDirection("SALE");
        incoming.setName("供货合同");
        incoming.setAmount(BigDecimal.TEN);
        incoming.setStatus("ACTIVE");
        Company initiator = new Company();
        initiator.setId(9L);
        initiator.setName("供应商");
        when(companyMapper.selectById(9L)).thenReturn(initiator);
        when(contractMapper.countPartyContracts(3L, null, null)).thenReturn(1L);
        when(contractMapper.selectPartyContracts(3L, null, null, 20, 0L)).thenReturn(List.of(incoming));

        ContractPayload view = service.pageContracts(null, null, 1, 20).items().get(0);

        assertThat(view.viewerCounterpartyCompanyId()).isEqualTo("9");
        assertThat(view.viewerCounterpartyName()).isEqualTo("供应商");
        assertThat(view.viewerDirection()).isEqualTo("PURCHASE");
        assertThat(view.perspective()).isEqualTo("INCOMING");
    }
}
