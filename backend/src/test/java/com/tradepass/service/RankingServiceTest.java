package com.tradepass.service;

import com.tradepass.common.TradePassDtos.HomePayload;
import com.tradepass.common.TradePassDtos.RankingItem;
import com.tradepass.entity.Company;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.TradeOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingServiceTest {
    private TradeOrderMapper orderMapper;
    private CompanyMapper companyMapper;
    private AccessControlService accessControl;
    private RankingService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(TradeOrderMapper.class);
        companyMapper = mock(CompanyMapper.class);
        accessControl = mock(AccessControlService.class);
        service = new RankingService(orderMapper, companyMapper, accessControl);
        when(accessControl.resolveCompanyId("3")).thenReturn(3L);
    }

    @Test
    void buildsSupplierHomeAndRanksRows() {
        Company company = new Company();
        company.setName("测试企业");
        when(companyMapper.selectById(3L)).thenReturn(company);
        when(orderMapper.selectRanking(3L, "SALE", "month")).thenReturn(List.of(
                Map.of("counterpartyName", "甲方", "totalAmount", new BigDecimal("12.50"), "orderCount", 2),
                Map.of("counterpartyName", "乙方", "totalAmount", new BigDecimal("7.00"), "orderCount", 1)
        ));

        HomePayload result = service.supplierHome("MONTH", "3");

        assertThat(result.companyName()).isEqualTo("测试企业");
        assertThat(result.role()).isEqualTo("SUPPLIER");
        assertThat(result.ranking()).extracting(RankingItem::rank).containsExactly(1, 2);
        assertThat(result.ranking().get(0).counterpartyName()).isEqualTo("甲方");
    }

    @Test
    void normalizesUnknownPeriodAndHandlesMissingCompany() {
        when(orderMapper.selectRanking(3L, "PURCHASE", "total")).thenReturn(List.of());

        HomePayload result = service.buyerHome("quarter", "3");

        assertThat(result.companyName()).isEqualTo("未知企业");
        assertThat(result.ranking()).isEmpty();
        verify(orderMapper).selectRanking(3L, "PURCHASE", "total");
    }
}
