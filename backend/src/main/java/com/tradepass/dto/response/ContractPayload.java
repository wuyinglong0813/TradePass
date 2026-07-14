package com.tradepass.dto.response;

import java.math.BigDecimal;

public record ContractPayload(String id, String counterpartyName, String name, String templateName, BigDecimal amount, String startDate, String endDate, String terms, String status, String initiatedBy, String createdAt) {
}
