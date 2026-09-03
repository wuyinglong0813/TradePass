-- TradePass 完整重置脚本：删除全部项目表及数据，然后由新版后端重建。
-- 请先在数据库客户端选中目标 TradePass 专用库，备份并停止所有后端实例。
-- 仅适用于明确要清空数据重新验收的环境；不要在保留现有数据的升级中执行。
-- 此脚本不删除数据库本身、数据库账号、云存储对象或法大大侧的记录。
-- 执行完成后必须启动包含 V26 的新版后端，并发布同版小程序。
SELECT DATABASE() AS database_to_reset;

SET @tradepass_reset_fk_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `approval_result_notification`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `auth_session`;
DROP TABLE IF EXISTS `bilateral_action_request`;
DROP TABLE IF EXISTS `business_document`;
DROP TABLE IF EXISTS `business_document_item`;
DROP TABLE IF EXISTS `business_document_template`;
DROP TABLE IF EXISTS `business_memo`;
DROP TABLE IF EXISTS `company`;
DROP TABLE IF EXISTS `company_certification_application`;
DROP TABLE IF EXISTS `company_invite`;
DROP TABLE IF EXISTS `company_member`;
DROP TABLE IF EXISTS `contract_archive`;
DROP TABLE IF EXISTS `contract_attachment`;
DROP TABLE IF EXISTS `contract_template`;
DROP TABLE IF EXISTS `counterparty_relation`;
DROP TABLE IF EXISTS `fadada_callback_event`;
DROP TABLE IF EXISTS `fadada_contract_sign_task`;
DROP TABLE IF EXISTS `fadada_corp_identity`;
DROP TABLE IF EXISTS `fadada_corp_seal`;
DROP TABLE IF EXISTS `fadada_user_identity`;
DROP TABLE IF EXISTS `inventory_balance`;
DROP TABLE IF EXISTS `inventory_inbound`;
DROP TABLE IF EXISTS `inventory_inbound_item`;
DROP TABLE IF EXISTS `inventory_product`;
DROP TABLE IF EXISTS `inventory_transaction`;
DROP TABLE IF EXISTS `inventory_transfer`;
DROP TABLE IF EXISTS `logistics_document`;
DROP TABLE IF EXISTS `perm_def`;
DROP TABLE IF EXISTS `project_contract_assignment`;
DROP TABLE IF EXISTS `project_contract_prompt_preference`;
DROP TABLE IF EXISTS `project_ledger`;
DROP TABLE IF EXISTS `reconciliation_entry`;
DROP TABLE IF EXISTS `reconciliation_statement`;
DROP TABLE IF EXISTS `role_def`;
DROP TABLE IF EXISTS `sales_order_receipt`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `template_category`;
DROP TABLE IF EXISTS `trade_contract`;
DROP TABLE IF EXISTS `trade_order`;
DROP TABLE IF EXISTS `warehouse`;

-- 迁移历史必须与上面的所有项目表一同删除，不能单独清空它的记录。
DROP TABLE IF EXISTS `flyway_schema_history`;

SET FOREIGN_KEY_CHECKS = @tradepass_reset_fk_checks;
