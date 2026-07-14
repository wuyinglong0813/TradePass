package com.tradepass.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeOrderPayload(String id, String direction, String counterpartyName, BigDecimal amount, LocalDate orderDate, String status) {
}
