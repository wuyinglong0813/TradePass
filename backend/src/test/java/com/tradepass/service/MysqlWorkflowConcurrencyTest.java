package com.tradepass.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.ApplicationIds;
import com.tradepass.config.SystemPermissionInitializer;
import com.tradepass.entity.SysUser;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.FadadaCallbackEvent;
import com.tradepass.mapper.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt in with -Dtradepass.test.mysql.url=jdbc:mysql://.../tradepass_fix_validation_... . */
@EnabledIfSystemProperty(named = "tradepass.test.mysql.url", matches = ".+")
class MysqlWorkflowConcurrencyTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static BusinessDocumentMapper documents;
    private static TradeContractMapper contracts;
    private static FadadaCallbackEventMapper events;
    private static SysUserMapper users;
    private SalesOrderInventoryService inventory;
    private BusinessDocumentService documentService;
    private long documentId;
    private long warehouseId;

    @BeforeAll static void database() throws Exception {
        String url = System.getProperty("tradepass.test.mysql.url");
        if (!url.matches("jdbc:mysql://[^/]+/tradepass_fix_validation_[a-zA-Z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("Only a dedicated tradepass_fix_validation_* database is allowed");
        }
        var dataSource = new DriverManagerDataSource(url,
                System.getProperty("tradepass.test.mysql.username", "root"),
                System.getProperty("tradepass.test.mysql.password", ""));
        jdbc = new JdbcTemplate(dataSource);
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        if (flyway.info().current() == null) {
            Flyway.configure().dataSource(dataSource).target("24").load().migrate();
            jdbc.update("""
                    INSERT INTO fadada_callback_event
                        (event_id, event_type, subject_type, payload_sha256, status)
                    VALUES ('legacy-before-v25', 'test', 'CALLBACK', ?, 'FAILED')
                    """, "0".repeat(64));
        }
        flyway.migrate();
        flyway.validate();
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(BusinessDocumentMapper.class);
        config.addMapper(TradeContractMapper.class);
        config.addMapper(FadadaCallbackEventMapper.class);
        config.addMapper(SysUserMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        documents = session.getMapper(BusinessDocumentMapper.class);
        contracts = session.getMapper(TradeContractMapper.class);
        events = session.getMapper(FadadaCallbackEventMapper.class);
        users = session.getMapper(SysUserMapper.class);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.update("INSERT IGNORE INTO sys_user(id,openid) VALUES (7,'fix-test-7'),(8,'fix-test-8')");
        jdbc.update("""
                INSERT IGNORE INTO company(id,name,credit_code,legal_person_name,created_by)
                VALUES (3,'供方测试','TEST-SUPPLIER','供方法人',7),(4,'需方测试','TEST-BUYER','需方法人',8)
                """);
    }

    @BeforeEach void fixtures() {
        var access = mock(AccessControlService.class);
        var audit = mock(AuditLogService.class);
        var reconciliation = new ReconciliationAccountService(jdbc, mock(CounterpartyRelationMapper.class), access);
        inventory = new SalesOrderInventoryService(jdbc, documents, contracts, access, audit,
                new ObjectMapper(), reconciliation, mock(SalesOrderSignatureService.class), mock(UserIdentityService.class));
        documentService = new BusinessDocumentService(mock(BusinessDocumentTemplateMapper.class), documents,
                contracts, mock(CompanyMapper.class), access, audit, new ObjectMapper());
        long contractId = IDS.incrementAndGet();
        documentId = IDS.incrementAndGet();
        warehouseId = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO trade_contract(id,company_id,counterparty_company_id,counterparty_name,
                    name,amount,status,initiated_by,direction,contract_no)
                VALUES (?,3,4,'需方测试','并发回归',20,'ACTIVE',7,'SALE',?)
                """, contractId, "HT-FIX-" + contractId);
        jdbc.update("""
                INSERT INTO business_document(id,company_id,recipient_company_id,contract_id,document_type,
                    document_no,template_id,template_name,content,created_by,status,supplier_company_id,buyer_company_id)
                VALUES (?,3,4,?,'SALES_ORDER',?,0,'测试模板','{}',7,'ISSUED',3,4)
                """, documentId, contractId, "FIX-" + documentId);
        jdbc.update("INSERT INTO warehouse(id,company_id,name,created_by) VALUES (?,4,?,8)", warehouseId, "仓库-" + warehouseId);
        item(1, "2");
    }

    @AfterEach void clearAuth() { AuthContext.clear(); }

    @Test void confirmationWinsWithdrawalAndKeepsInventoryAndLedgerConsistent() throws Exception {
        race(4, this::receive, 3, () -> documentService.withdraw(documentId));
        assertThat(status()).isEqualTo("INBOUNDED");
        assertThat(count("inventory_inbound", "source_document_id")).isEqualTo(1);
        assertThat(count("sales_order_receipt", "document_id")).isEqualTo(1);
        assertThat(count("reconciliation_entry", "source_id")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_balance WHERE warehouse_id = ?",
                BigDecimal.class, warehouseId)).isEqualByComparingTo("2");
    }

    @Test void withdrawalWinsConfirmationWithoutInventoryReceiptOrLedger() throws Exception {
        race(3, () -> documentService.withdraw(documentId), 4, this::receive);
        assertThat(status()).isEqualTo("WITHDRAWN");
        assertThat(count("inventory_inbound", "source_document_id")).isZero();
        assertThat(count("sales_order_receipt", "document_id")).isZero();
        assertThat(count("reconciliation_entry", "source_id")).isZero();
    }

    @Test void duplicateConfirmationPostsInventoryAndLedgerOnce() {
        asCompany(4, this::receive);
        asCompany(4, this::receive);
        assertThat(count("inventory_inbound", "source_document_id")).isEqualTo(1);
        assertThat(count("reconciliation_entry", "source_id")).isEqualTo(1);
    }

    @Test void repeatedProductLinesDeductTheirCombinedQuantity() {
        long sourceWarehouse = returnFixture("3", "4");
        asCompany(3, this::receive);
        assertThat(balance(sourceWarehouse)).isEqualByComparingTo("3");
        assertThat(balance(warehouseId)).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("SELECT inventory_amount FROM inventory_balance WHERE warehouse_id = ?",
                BigDecimal.class, sourceWarehouse)).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE warehouse_id = ? AND biz_type = 'RETURN_ORDER_OUTBOUND'",
                Integer.class, sourceWarehouse)).isEqualTo(1);
    }

    @Test void repeatedLinesExceedingTotalStockRollBackEverything() {
        long sourceWarehouse = returnFixture("6", "6");
        assertThatThrownBy(() -> asCompany(3, this::receive)).isInstanceOf(BusinessException.class).hasMessageContaining("库存不足");
        assertThat(status()).isEqualTo("ISSUED");
        assertThat(balance(sourceWarehouse)).isEqualByComparingTo("10");
        assertThat(balance(warehouseId)).isNull();
        assertThat(count("inventory_transfer", "source_document_id")).isZero();
        assertThat(count("sales_order_receipt", "document_id")).isZero();
        assertThat(count("reconciliation_entry", "source_id")).isZero();
    }

    @Test void callbackClaimRejectsConcurrentWorkersAndRecoversExpiredLease() {
        FadadaCallbackEvent event = event();
        LocalDateTime now = LocalDateTime.now();
        assertThat(events.claim(event.getId(), "first", now, now.plusMinutes(5))).isEqualTo(1);
        assertThat(events.claim(event.getId(), "duplicate", now, now.plusMinutes(5))).isZero();
        assertThat(events.findDue(now)).doesNotContain(event.getId());
        LocalDateTime later = now.plusMinutes(6);
        assertThat(events.findDue(later)).contains(event.getId());
        assertThat(events.claim(event.getId(), "recovered", later, later.plusMinutes(5))).isEqualTo(1);
        event.setStatus("PROCESSED"); event.setProcessedAt(later);
        assertThat(events.finish(event, "first")).isZero();
        assertThat(events.finish(event, "recovered")).isEqualTo(1);
        assertThat(events.claim(event.getId(), "late", later, later.plusMinutes(5))).isZero();
        assertThat(events.selectById(event.getId()).getAttemptCount()).isEqualTo(2);
    }

    @Test void failedCallbackWaitsForBackoffThenCanRetry() {
        FadadaCallbackEvent event = event();
        LocalDateTime now = LocalDateTime.now();
        events.claim(event.getId(), "first", now, now.plusMinutes(5));
        event.setStatus("FAILED"); event.setNextAttemptAt(now.plusMinutes(1));
        events.finish(event, "first");
        assertThat(events.claim(event.getId(), "early", now, now.plusMinutes(5))).isZero();
        assertThat(events.claim(event.getId(), "retry", now.plusMinutes(2), now.plusMinutes(7))).isEqualTo(1);
    }

    @Test void migrationPreservesLegacyEventAndRedeliveryCanRestoreItsPayload() {
        Long id = jdbc.queryForObject("SELECT id FROM fadada_callback_event WHERE event_id = 'legacy-before-v25'", Long.class);
        events.restoreLegacyPayload(id, "{\"signTaskId\":\"legacy\"}", LocalDateTime.now());
        assertThat(events.selectById(id).getRetryPayload()).contains("legacy");
        assertThat(events.restoreLegacyPayload(id, "{}", LocalDateTime.now())).isZero();
    }

    @Test void noBusinessTableRequiresDatabaseGeneratedIds() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND extra LIKE '%auto_increment%'
                """, Integer.class)).isZero();
        SysUser first = new SysUser(); first.setOpenid("id-test-" + ApplicationIds.next()); first.setStatus("ACTIVE");
        users.insert(first);
        assertThat(first.getId()).isGreaterThan(9007199254740991L);
        jdbc.update("DELETE FROM sys_user WHERE id = ?", first.getId());
        SysUser second = new SysUser(); second.setOpenid(first.getOpenid()); second.setStatus("ACTIVE");
        users.insert(second);
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(users.selectById(second.getId()).getOpenid()).isEqualTo(second.getOpenid());
    }

    @Test void systemPermissionsAreRestoredWithoutCreatingBusinessData() {
        Integer usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        Integer companiesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM company", Integer.class);
        jdbc.update("DELETE FROM perm_def");
        var initializer = new SystemPermissionInitializer(jdbc);
        initializer.run(null);
        initializer.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM perm_def", Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company", Integer.class)).isEqualTo(companiesBefore);
    }

    @Test void projectsWarehousesAndMemoUpsertsUseApplicationIds() {
        AuthContext.set(7L, 3L);
        long contractId = documents.selectById(documentId).getContractId();
        var access = mock(AccessControlService.class);
        var audit = mock(AuditLogService.class);
        var projects = new ProjectLedgerService(jdbc, access, audit);
        Map<String, Object> project = projects.createProject("", "ID 回归-" + documentId, "测试");
        long projectId = (Long) project.get("id");
        assertThat(projectId).isGreaterThan(9007199254740991L);
        projects.assignContracts(projectId, List.of(contractId));
        projects.dismissContractPrompt(contractId);
        assertThat(jdbc.queryForObject("SELECT project_id FROM project_contract_assignment WHERE contract_id = ?",
                Long.class, contractId)).isEqualTo(projectId);
        assertThat((Long) inventory.createWarehouse("ID 仓库-" + documentId, "").get("id"))
                .isGreaterThan(9007199254740991L);
        var memos = new PersonalMemoService(jdbc, contracts, documents, access, audit);
        memos.save("CONTRACT", contractId, "第一版");
        Long memoId = jdbc.queryForObject("SELECT id FROM business_memo WHERE biz_id = ?", Long.class, contractId);
        memos.save("CONTRACT", contractId, "第二版");
        assertThat(memos.get("CONTRACT", contractId)).containsEntry("content", "第二版");
        assertThat(jdbc.queryForObject("SELECT id FROM business_memo WHERE biz_id = ?", Long.class, contractId))
                .isEqualTo(memoId).isGreaterThan(9007199254740991L);
    }

    @Test void attachmentStatementAndArchiveMetadataUseExactGeneratedIds() throws Exception {
        AuthContext.set(7L, 3L);
        long contractId = documents.selectById(documentId).getContractId();
        var access = mock(AccessControlService.class);
        var audit = mock(AuditLogService.class);
        var storage = mock(ObjectStorageService.class);
        var properties = new com.tradepass.config.StorageProperties();
        Map<String, byte[]> blobs = new java.util.HashMap<>();
        when(storage.putImmutable(anyString(), any(byte[].class), anyString(), anyString())).thenAnswer(call -> {
            String key = call.getArgument(0); byte[] data = call.getArgument(1);
            blobs.put(key, data);
            return new ObjectStorageService.StoredObject("CLOUDBASE_COS", "test", key, "v1", "etag",
                    "CLOUDBASE_MANAGED", data.length, call.getArgument(3));
        });
        when(storage.get(any(ObjectStorageService.ObjectReference.class))).thenAnswer(call ->
                blobs.get(call.<ObjectStorageService.ObjectReference>getArgument(0).objectKey()));
        var relations = mock(CounterpartyRelationMapper.class);
        when(relations.countActiveBetween(3L, 4L)).thenReturn(1L);
        byte[] xlsx;
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("对账单"); workbook.write(output); xlsx = output.toByteArray();
        }
        byte[] pdf = "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (boolean cloud : List.of(false, true)) {
            when(storage.isEnabled()).thenReturn(cloud);
            var attachments = new ContractAttachmentService(jdbc, contracts, access, audit, storage, properties);
            long attachmentId = (Long) attachments.upload(contractId, "OTHER", "说明.pdf", pdf, null, null).get("id");
            assertThat(attachmentId).isGreaterThan(9007199254740991L);
            assertThat(attachments.getFile(attachmentId).data()).containsExactly(pdf);
            var statements = new ReconciliationStatementService(jdbc, relations, access, audit, storage, properties);
            long statementId = (Long) statements.upload(4L, "2026-09", "", "对账.xlsx", xlsx).get("id");
            assertThat(statementId).isGreaterThan(9007199254740991L);
            assertThat(statements.getFile(statementId).data()).containsExactly(xlsx);
        }
        var pdfService = mock(ContractPdfService.class);
        when(pdfService.fileName(any())).thenReturn("合同.pdf");
        when(pdfService.generate(any())).thenReturn(pdf);
        var archives = new ContractArchiveService(jdbc, pdfService, storage, properties);
        for (int version = 1; version <= 2; version++) {
            var payload = new ObjectMapper().convertValue(Map.of("id", Long.toString(contractId),
                    "companyId", "3", "status", "ACTIVE", "versionNo", version),
                    com.tradepass.dto.response.ContractPayload.class);
            if (version == 1) archives.archiveSignedPdf(payload, pdf, "provider-test", 7L);
            else archives.archiveOnApproval(payload, 7L);
            assertThat(archives.getPdf(payload, 7L).data()).containsExactly(pdf);
        }
        assertThat(jdbc.queryForObject("SELECT MIN(id) FROM contract_archive WHERE contract_id = ?",
                Long.class, contractId)).isGreaterThan(9007199254740991L);
    }

    @Test void bilateralVoidLinksGeneratedRequestIdToInventoryReversalLedgerAndNotification() {
        asCompany(4, this::receive);
        var access = mock(AccessControlService.class);
        var ledger = new ReconciliationAccountService(jdbc, mock(CounterpartyRelationMapper.class), access);
        var approvals = new ApprovalService(jdbc, access);
        var bilateral = new BilateralActionService(jdbc, contracts, access, mock(AuditLogService.class),
                ledger, inventory, approvals);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) asCompany(3,
                () -> bilateral.request("BUSINESS_DOCUMENT", documentId, "VOID", "ID 回归作废", false));
        long requestId = (Long) result.get("id");
        assertThat(requestId).isGreaterThan(9007199254740991L);
        asCompany(4, () -> bilateral.decide(requestId, "APPROVE", ""));
        assertThat(status()).isEqualTo("VOIDED");
        assertThat(balance(warehouseId)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT action_request_id FROM reconciliation_entry WHERE source_id = ? AND reversal_of_id IS NOT NULL",
                Long.class, requestId)).isEqualTo(requestId);
        assertThat(jdbc.queryForObject("SELECT id FROM approval_result_notification WHERE source_id = ? AND result_type = 'BILATERAL_ACTION'",
                Long.class, requestId)).isGreaterThan(9007199254740991L);
    }

    private void race(long firstCompany, Supplier<?> firstAction, long secondCompany, Supplier<?> secondAction) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstReady = new CountDownLatch(1), secondStarted = new CountDownLatch(1), commit = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> asCompany(firstCompany, () -> {
                Object value = firstAction.get(); firstReady.countDown(); await(commit); return value;
            }));
            assertThat(firstReady.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(() -> {
                secondStarted.countDown();
                return asCompany(secondCompany, secondAction);
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            commit.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class);
        } finally { commit.countDown(); pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }

    private Object asCompany(long companyId, Supplier<?> action) {
        AuthContext.set(companyId == 3 ? 7L : 8L, companyId);
        try { return tx.execute(status -> action.get()); }
        finally { AuthContext.clear(); }
    }
    private Object receive() { return inventory.receive(documentId, "INBOUND", warehouseId, null, "签名.png", new byte[]{1}); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for transaction"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    private String status() { return jdbc.queryForObject("SELECT status FROM business_document WHERE id = ?", String.class, documentId); }
    private int count(String table, String column) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, documentId); }
    private BigDecimal balance(long warehouse) { return jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_balance WHERE warehouse_id = ?", BigDecimal.class, warehouse); }
    private void item(int line, String quantity) {
        jdbc.update("""
                INSERT INTO business_document_item(id,document_id,issuer_company_id,recipient_company_id,line_no,
                    product_name,specification,base_unit,quantity,unit_price,amount)
                VALUES (?,?,3,4,?,'相同商品','A','件',?,2,?)
                """, ApplicationIds.next(), documentId, line, new BigDecimal(quantity), new BigDecimal(quantity).multiply(BigDecimal.valueOf(2)));
    }
    private long returnFixture(String first, String second) {
        long source = IDS.incrementAndGet(), product = IDS.incrementAndGet();
        jdbc.update("UPDATE warehouse SET company_id = 3 WHERE id = ?", warehouseId);
        jdbc.update("INSERT INTO warehouse(id,company_id,name,created_by) VALUES (?,4,?,8)", source, "退货仓-" + source);
        jdbc.update("INSERT INTO inventory_product(id,company_id,product_name,specification,base_unit) VALUES (?,4,?,'A','件')",
                product, "相同商品-" + documentId);
        jdbc.update("INSERT INTO inventory_balance(id,company_id,warehouse_id,product_id,quantity,unit_price,inventory_amount) VALUES (?,4,?,?,10,2,20)", ApplicationIds.next(), source, product);
        jdbc.update("UPDATE business_document SET company_id=4,recipient_company_id=3,document_type='RETURN_ORDER',outbound_warehouse_id=? WHERE id=?", source, documentId);
        jdbc.update("UPDATE business_document_item SET quantity=?,amount=? WHERE document_id=?", new BigDecimal(first), new BigDecimal(first).multiply(BigDecimal.valueOf(2)), documentId);
        item(2, second);
        jdbc.update("UPDATE business_document_item SET product_name=? WHERE document_id=?", "相同商品-" + documentId, documentId);
        return source;
    }
    private FadadaCallbackEvent event() {
        FadadaCallbackEvent event = new FadadaCallbackEvent();
        event.setEventId("test-" + IDS.incrementAndGet()); event.setEventType("test"); event.setSubjectType("CALLBACK");
        event.setPayloadSha256("0".repeat(64)); event.setRetryPayload("{\"signTaskId\":\"test\"}");
        event.setStatus("RECEIVED"); event.setAttemptCount(0); event.setReceivedAt(LocalDateTime.now());
        events.insert(event); return event;
    }
}
