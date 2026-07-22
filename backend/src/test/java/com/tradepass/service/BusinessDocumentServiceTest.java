package com.tradepass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.entity.BusinessDocument;
import com.tradepass.entity.BusinessDocumentTemplate;
import com.tradepass.entity.Company;
import com.tradepass.entity.TradeContract;
import com.tradepass.mapper.BusinessDocumentMapper;
import com.tradepass.mapper.BusinessDocumentTemplateMapper;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.TradeContractMapper;
import com.tradepass.support.MybatisTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessDocumentServiceTest {
    private BusinessDocumentTemplateMapper templateMapper;
    private BusinessDocumentMapper documentMapper;
    private TradeContractMapper contractMapper;
    private CompanyMapper companyMapper;
    private BusinessDocumentService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MybatisTestSupport.initialize(
                BusinessDocumentTemplate.class, BusinessDocument.class,
                TradeContract.class, Company.class
        );
        templateMapper = mock(BusinessDocumentTemplateMapper.class);
        documentMapper = mock(BusinessDocumentMapper.class);
        contractMapper = mock(TradeContractMapper.class);
        companyMapper = mock(CompanyMapper.class);
        objectMapper = new ObjectMapper();
        service = new BusinessDocumentService(
                templateMapper,
                documentMapper,
                contractMapper,
                companyMapper,
                mock(AccessControlService.class),
                mock(AuditLogService.class),
                objectMapper
        );
        AuthContext.set(7L, 3L);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void createsDocumentFromSelectedTemplateAndEditedSnapshot() throws Exception {
        BusinessDocumentTemplate template = new BusinessDocumentTemplate();
        template.setId(11L);
        template.setCompanyId(3L);
        template.setDocumentType(BusinessDocumentService.SALES_ORDER);
        template.setName("标准销售单");
        template.setContent("{\"columns\":[\"序号\",\"品名\",\"数量\",\"金额\"],\"blankRows\":8}");
        when(templateMapper.selectOne(any())).thenReturn(template);

        TradeContract contract = new TradeContract();
        contract.setId(21L);
        contract.setCompanyId(3L);
        contract.setCounterpartyName("原往来单位");
        contract.setContractNo("HT-21");
        contract.setAmount(new BigDecimal("99.00"));
        contract.setTerms("{\"sections\":[]}");
        when(contractMapper.selectById(21L)).thenReturn(contract);

        Company company = new Company();
        company.setId(3L);
        company.setName("原制单企业");
        when(companyMapper.selectById(3L)).thenReturn(company);

        AtomicReference<BusinessDocument> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            BusinessDocument document = invocation.getArgument(0);
            document.setId(31L);
            inserted.set(document);
            return 1;
        }).when(documentMapper).insert(any(BusinessDocument.class));
        when(documentMapper.selectById(31L)).thenAnswer(invocation -> inserted.get());

        Map<String, Object> editedContent = Map.of(
                "title", "七月销售单",
                "companyName", "河北测试公司",
                "counterpartyName", "北京客户公司",
                "contractNo", "HT-EDITED",
                "date", "2026-07-22",
                "columns", List.of("序号", "品名", "数量", "金额"),
                "rows", List.of(List.of("1", "测试商品", "2", "88.50")),
                "blankRows", 8,
                "totalAmount", "88.5"
        );

        Map<String, Object> result = service.createDocument(21L, Map.of(
                "documentType", BusinessDocumentService.SALES_ORDER,
                "templateId", 11L,
                "content", editedContent
        ));

        assertThat(result).containsEntry("id", 31L).containsEntry("templateName", "标准销售单");
        BusinessDocument document = inserted.get();
        assertThat(document.getTemplateId()).isEqualTo(11L);
        JsonNode content = objectMapper.readTree(document.getContent());
        assertThat(content.path("title").asText()).isEqualTo("七月销售单");
        assertThat(content.path("companyName").asText()).isEqualTo("河北测试公司");
        assertThat(content.path("counterpartyName").asText()).isEqualTo("北京客户公司");
        assertThat(content.path("rows").get(0).get(1).asText()).isEqualTo("测试商品");
        assertThat(content.path("totalAmount").asText()).isEqualTo("88.5");
        assertThat(content.path("templateName").asText()).isEqualTo("标准销售单");
    }
}
