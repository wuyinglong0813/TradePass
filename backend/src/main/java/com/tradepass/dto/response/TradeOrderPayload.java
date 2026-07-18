package com.tradepass.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeOrderPayload(
        String id,
        String direction,
        String counterpartyName,
        String orderNo,
        BigDecimal amount,
        LocalDate orderDate,
        String status
) {
    public TradeOrderPayload(String id, String direction, String counterpartyName, BigDecimal amount,
                             LocalDate orderDate, String status) {
        this(id, direction, counterpartyName, null, amount, orderDate, status);
    }
}
