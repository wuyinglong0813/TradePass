package com.tradepass.service;

import com.tradepass.common.TradePassDtos.HomePayload;
import com.tradepass.common.TradePassDtos.RankingItem;
import com.tradepass.entity.Company;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.TradeOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RankingService {
    private final TradeOrderMapper tradeOrderMapper;
    private final CompanyMapper companyMapper;
    private final AccessControlService accessControlService;

    public RankingService(TradeOrderMapper tradeOrderMapper, CompanyMapper companyMapper, AccessControlService accessControlService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.companyMapper = companyMapper;
        this.accessControlService = accessControlService;
    }

    public HomePayload supplierHome(String period, String companyId) {
        long cid = accessControlService.resolveCompanyId(companyId);
        return new HomePayload(String.valueOf(cid), loadCompanyName(cid), "SUPPLIER", "我是供应商",
                List.of("total", "year", "month"), rank("SALE", period, cid));
    }

    public HomePayload buyerHome(String period, String companyId) {
        long cid = accessControlService.resolveCompanyId(companyId);
        return new HomePayload(String.valueOf(cid), loadCompanyName(cid), "BUYER", "我是采购商",
                List.of("total", "year", "month"), rank("PURCHASE", period, cid));
    }

    public List<RankingItem> salesRanking(String period, String companyId) {
        return rank("SALE", period, accessControlService.resolveCompanyId(companyId));
    }

    public List<RankingItem> purchaseRanking(String period, String companyId) {
        return rank("PURCHASE", period, accessControlService.resolveCompanyId(companyId));
    }

    private List<RankingItem> rank(String direction, String period, long companyId) {
        List<Map<String, Object>> rows = tradeOrderMapper.selectRanking(companyId, direction, normalizePeriod(period));
        List<RankingItem> ranked = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            ranked.add(new RankingItem(
                    rank++,
                    string(row.get("counterpartyName")),
                    (BigDecimal) row.get("totalAmount"),
                    ((Number) row.get("orderCount")).intValue(),
                    "FLAT"
            ));
        }
        return ranked;
    }

    private String loadCompanyName(long companyId) {
        Company company = companyMapper.selectById(companyId);
        return company == null ? "未知企业" : company.getName();
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "total";
        }
        String normalized = period.toLowerCase();
        return List.of("total", "year", "month").contains(normalized) ? normalized : "total";
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
