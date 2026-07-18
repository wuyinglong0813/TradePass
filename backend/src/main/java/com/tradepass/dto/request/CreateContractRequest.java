package com.tradepass.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateContractRequest(
        @NotBlank String counterpartyName,
        @NotBlank String name,
        String templateName,
        @NotNull @DecimalMin(value = "0.00", message = "合同金额不能为负数") BigDecimal amount,
        String startDate,
        String endDate,
        String terms,
        Long counterpartyCompanyId,
        @Pattern(regexp = "SALE|PURCHASE", message = "交易方向只能是 SALE 或 PURCHASE") String direction,
        @Size(max = 64) String contractNo,
        @Size(max = 64) String clientRequestId
) {
    public CreateContractRequest(String counterpartyName, String name, String templateName, BigDecimal amount,
                                 String startDate, String endDate, String terms) {
        this(counterpartyName, name, templateName, amount, startDate, endDate, terms,
                null, null, null, null);
    }
}
