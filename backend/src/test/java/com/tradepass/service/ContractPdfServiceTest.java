package com.tradepass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.tradepass.dto.response.ContractPayload;
import com.tradepass.mapper.CompanyMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContractPdfServiceTest {

    @Test
    void contractNameOverridesLegacySnapshotTitle() throws Exception {
        ContractPdfService service = new ContractPdfService(
                new ObjectMapper(), mock(CompanyMapper.class));
        ContractPayload contract = new ContractPayload(
                "1", "HT-TEST", "1", "2", "对方企业", "SALE",
                "测试123", "标准模板", new BigDecimal("100"),
                "2026-07-22", null, "{\"title\":\"购销合同\"}",
                "ACTIVE", 1, "1", null, null, "2026-07-22T12:00:00",
                "1", "2", "对方企业", "SALE", "OUTGOING");

        PdfReader reader = new PdfReader(service.generate(contract));
        try {
            assertThat(reader.getInfo()).containsEntry("Title", "测试123");
        } finally {
            reader.close();
        }
    }
}
