package com.tradepass.dto.response;

import java.math.BigDecimal;

public record ContractPayload(
        String id,
        String contractNo,
        String companyId,
        String counterpartyCompanyId,
        String counterpartyName,
        String direction,
        String name,
        String templateName,
        BigDecimal amount,
        String startDate,
        String endDate,
        String terms,
        String status,
        Integer versionNo,
        String initiatedBy,
        String approvedBy,
        String approvedAt,
        String createdAt,
        String viewerCompanyId,
        String viewerCounterpartyCompanyId,
        String viewerCounterpartyName,
        String viewerDirection,
        String perspective
) {
}
