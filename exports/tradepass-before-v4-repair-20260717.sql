-- MySQL dump 10.13  Distrib 8.4.9, for Linux (aarch64)
--
-- Host: localhost    Database: tradepass
-- ------------------------------------------------------
-- Server version	8.4.9

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `auth_session`
--

DROP TABLE IF EXISTS `auth_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_session` (
  `token_hash` char(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `expires_at` datetime NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token_hash`),
  KEY `idx_session_user` (`user_id`),
  KEY `idx_session_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auth_session`
--

LOCK TABLES `auth_session` WRITE;
/*!40000 ALTER TABLE `auth_session` DISABLE KEYS */;
INSERT INTO `auth_session` VALUES ('215a5933e88998246eb9a15bb63b63e15b6f7a8a3b5a76247710c5b3aaab9e00',1,'2026-07-24 13:54:38',0,'2026-07-17 05:54:37'),('49c1573117b73a640454cd091e057391216a61a2f8b9eb86b2c7724880742426',1,'2026-07-24 09:19:21',0,'2026-07-17 01:19:20'),('5e56966cdc309174e212e30654baee587fbafbe34bc3dab478746a8085bbcfda',1,'2026-07-24 13:52:01',0,'2026-07-17 05:52:00'),('ae7c44d8471d4c47c36034fbeaca74fa2193f5d176249a5417a43d2f39887be5',1,'2026-07-24 14:25:26',0,'2026-07-17 06:25:25'),('b740d36fa2ccab9cc1cc28021dc618a80e3cc3e6a4ce37afd489720786803304',1,'2026-07-22 14:44:35',0,'2026-07-15 06:44:34'),('c394700e0bfa2a2922c6a593a0ab6ef4afd111047b8266f58d90ea6748223c9c',1,'2026-07-22 15:23:54',0,'2026-07-15 07:23:53'),('c81023aba82d15755f04189399a7b7b04e16b6e0b492642c3f8d8633613e95b1',1,'2026-07-24 13:51:18',0,'2026-07-17 05:51:17'),('d47b4898271a58dcc3e918f32a5c42a85da787dd98ba115eaffb30b5bd958b99',1,'2026-07-22 14:46:31',0,'2026-07-15 06:46:30'),('fcd42c9dc9a866a9c26013741d34f139f955729e2279b1ac36355393de574b74',1,'2026-07-22 14:44:13',0,'2026-07-15 06:44:12');
/*!40000 ALTER TABLE `auth_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company`
--

DROP TABLE IF EXISTS `company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `credit_code` varchar(32) NOT NULL,
  `legal_person_name` varchar(64) NOT NULL,
  `registered_address` varchar(256) DEFAULT NULL,
  `contact_phone` varchar(32) DEFAULT NULL,
  `bank_name` varchar(128) DEFAULT NULL,
  `bank_account` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `certification_status` varchar(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
  `real_name_status` varchar(32) NOT NULL DEFAULT 'NOT_STARTED',
  `face_status` varchar(32) NOT NULL DEFAULT 'NOT_STARTED',
  `seal_status` varchar(32) NOT NULL DEFAULT 'NOT_UPLOADED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `credit_code` (`credit_code`)
) ENGINE=InnoDB AUTO_INCREMENT=76110231 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company`
--

LOCK TABLES `company` WRITE;
/*!40000 ALTER TABLE `company` DISABLE KEYS */;
INSERT INTO `company` VALUES (1,'河北光屿行贸易有限公司','91130100MA00000001','满帅',NULL,NULL,NULL,NULL,NULL,'PENDING_REVIEW','VERIFIED','VERIFIED','PENDING_REVIEW','2026-06-22 03:30:43','2026-06-23 11:23:13'),(2,'上海远航进出口有限公司','91310000MA00000002','王海',NULL,NULL,NULL,NULL,NULL,'VERIFIED','VERIFIED','VERIFIED','UPLOADED','2026-06-22 06:16:52','2026-06-22 06:16:52'),(76110230,'测试公司123','111','测试人123',NULL,NULL,NULL,NULL,NULL,'PENDING','NOT_STARTED','NOT_STARTED','NOT_UPLOADED','2026-06-29 07:52:56','2026-06-29 07:52:56');
/*!40000 ALTER TABLE `company` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_authorization`
--

DROP TABLE IF EXISTS `company_authorization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_authorization` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `member_user_id` bigint DEFAULT NULL,
  `member_name` varchar(64) NOT NULL,
  `role_code` varchar(64) NOT NULL,
  `permissions` json NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_authorization`
--

LOCK TABLES `company_authorization` WRITE;
/*!40000 ALTER TABLE `company_authorization` DISABLE KEYS */;
/*!40000 ALTER TABLE `company_authorization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_certification`
--

DROP TABLE IF EXISTS `company_certification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_certification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `license_file_url` varchar(512) NOT NULL,
  `audit_status` varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  `audit_remark` varchar(512) DEFAULT NULL,
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `audited_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_certification`
--

LOCK TABLES `company_certification` WRITE;
/*!40000 ALTER TABLE `company_certification` DISABLE KEYS */;
/*!40000 ALTER TABLE `company_certification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_invite`
--

DROP TABLE IF EXISTS `company_invite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_invite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `used_by` bigint DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `type` varchar(32) NOT NULL DEFAULT 'member',
  `relation_role` varchar(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_invite`
--

LOCK TABLES `company_invite` WRITE;
/*!40000 ALTER TABLE `company_invite` DISABLE KEYS */;
INSERT INTO `company_invite` VALUES (1,1,'5XUE2SB5',0,NULL,'2026-06-24 03:39:48','2026-06-23 03:39:48','member',NULL),(2,1,'DYPS5GTA',0,NULL,'2026-06-24 03:40:24','2026-06-23 03:40:24','member',NULL),(3,1,'BK8TYXFE',0,NULL,'2026-06-24 03:40:33','2026-06-23 03:40:33','member',NULL),(4,1,'EJ2WXRTP',0,NULL,'2026-06-24 03:40:36','2026-06-23 03:40:36','member',NULL),(5,1,'63CY65AF',0,NULL,'2026-06-24 03:42:27','2026-06-23 03:42:27','member',NULL),(6,1,'7RD6JTGJ',0,NULL,'2026-06-24 10:34:22','2026-06-23 10:34:22','member',NULL),(7,1,'62CL8R73',0,NULL,'2026-06-24 10:34:32','2026-06-23 10:34:32','member',NULL),(8,1,'Q75RH2G3',0,NULL,'2026-06-24 10:34:35','2026-06-23 10:34:35','member',NULL),(9,1,'K8B76YUU',0,NULL,'2026-06-24 10:35:04','2026-06-23 10:35:04','member',NULL),(10,1,'CJ6KF2R9',0,NULL,'2026-06-24 10:35:30','2026-06-23 10:35:30','member',NULL),(11,1,'R8TPMVWH',0,NULL,'2026-06-24 10:35:46','2026-06-23 10:35:46','member',NULL),(12,1,'PQD4SJEP',0,NULL,'2026-06-24 10:39:31','2026-06-23 10:39:31','member',NULL),(13,1,'4ZDTJK4M',0,NULL,'2026-06-24 10:39:33','2026-06-23 10:39:33','member',NULL),(14,1,'HQ7UQUQW',0,NULL,'2026-06-24 11:20:53','2026-06-23 11:20:53','member',NULL),(15,1,'V3DDLPU2',0,NULL,'2026-06-24 11:22:58','2026-06-23 11:22:58','member',NULL),(16,1,'PZG3AV57',0,NULL,'2026-06-24 11:23:39','2026-06-23 11:23:39','counterparty',NULL),(17,1,'KQRCYCZH',0,NULL,'2026-06-24 11:27:16','2026-06-23 11:27:16','member',NULL),(18,1,'64A5T669',0,NULL,'2026-06-24 11:27:19','2026-06-23 11:27:19','member',NULL),(19,1,'DAZK582S',0,NULL,'2026-06-24 11:27:21','2026-06-23 11:27:21','member',NULL),(20,1,'FRSPB8FM',0,NULL,'2026-06-24 11:27:53','2026-06-23 11:27:53','counterparty',NULL),(21,1,'STHL6HPZ',0,NULL,'2026-06-24 11:27:57','2026-06-23 11:27:57','counterparty',NULL),(22,1,'3DDGRRXD',0,NULL,'2026-06-24 11:27:58','2026-06-23 11:27:58','counterparty',NULL),(23,1,'YTPECMFN',0,NULL,'2026-06-24 11:27:59','2026-06-23 11:27:59','counterparty',NULL),(24,1,'3VYDZ29F',0,NULL,'2026-06-24 11:27:59','2026-06-23 11:27:59','counterparty',NULL),(25,1,'LGL3S2P9',0,NULL,'2026-06-24 11:29:30','2026-06-23 11:29:30','member',NULL),(26,1,'J6YBRTPS',0,NULL,'2026-06-24 11:45:43','2026-06-23 11:45:43','counterparty',NULL),(27,1,'M3WP33DM',0,NULL,'2026-06-25 01:17:21','2026-06-24 01:17:21','member',NULL),(28,1,'WBR28K7Q',0,NULL,'2026-06-25 01:23:25','2026-06-24 01:23:25','member',NULL),(29,1,'B7Y43GYH',0,NULL,'2026-06-28 05:52:19','2026-06-27 05:52:19','counterparty',NULL),(30,1,'F3VWVE69',0,NULL,'2026-06-28 05:52:24','2026-06-27 05:52:24','counterparty',NULL),(31,1,'TDGP2U5M',0,NULL,'2026-06-28 05:52:41','2026-06-27 05:52:41','counterparty',NULL),(32,1,'WGGANB5P',0,NULL,'2026-06-28 05:54:39','2026-06-27 05:54:39','counterparty',NULL),(33,1,'GA9PLBK2',0,NULL,'2026-06-28 05:55:50','2026-06-27 05:55:50','counterparty',NULL),(34,1,'ZBW9YJCB',0,NULL,'2026-06-28 05:57:06','2026-06-27 05:57:06','counterparty',NULL),(35,1,'C56BCQ95',0,NULL,'2026-06-28 05:57:53','2026-06-27 05:57:53','counterparty',NULL),(36,1,'LXPDX9Z7',0,NULL,'2026-07-16 16:49:35','2026-07-15 08:49:35','counterparty','supplier'),(37,1,'JY2LR9Y4',0,NULL,'2026-07-18 13:53:05','2026-07-17 05:53:04','counterparty','supplier'),(38,1,'BMF9CJ7F',0,NULL,'2026-07-18 13:53:16','2026-07-17 05:53:16','counterparty','supplier');
/*!40000 ALTER TABLE `company_invite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_member`
--

DROP TABLE IF EXISTS `company_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role_code` varchar(64) NOT NULL,
  `is_legal_person` tinyint(1) NOT NULL DEFAULT '0',
  `is_administrator` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `custom_permissions` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_user` (`company_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=376 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_member`
--

LOCK TABLES `company_member` WRITE;
/*!40000 ALTER TABLE `company_member` DISABLE KEYS */;
INSERT INTO `company_member` VALUES (1,1,1,'LEGAL',1,0,'ACTIVE','2026-06-22 03:30:43',NULL),(3,1,3,'SALES',0,0,'ACTIVE','2026-06-22 03:30:43',NULL),(5,1,5,'ADMIN',0,1,'ACTIVE','2026-06-22 03:30:43',NULL),(6,1,6,'SALES',0,0,'ACTIVE','2026-06-22 03:42:27',NULL),(29,2,1,'SALES',0,0,'ACTIVE','2026-06-22 06:16:52',NULL),(104,1,2,'PURCHASER',0,0,'ACTIVE','2026-06-23 11:33:15',NULL),(136,1,4,'FINANCE',0,0,'ACTIVE','2026-06-25 02:36:22',NULL),(157,76110230,10,'LEGAL',1,0,'ACTIVE','2026-06-29 07:52:56',NULL),(188,1,7,'LEGAL',1,0,'ACTIVE','2026-06-29 08:24:34',NULL),(189,1,8,'LEGAL',1,0,'ACTIVE','2026-06-29 09:50:57',NULL);
/*!40000 ALTER TABLE `company_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_seal`
--

DROP TABLE IF EXISTS `company_seal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_seal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `file_url` varchar(512) NOT NULL,
  `usage_scope` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_seal`
--

LOCK TABLES `company_seal` WRITE;
/*!40000 ALTER TABLE `company_seal` DISABLE KEYS */;
/*!40000 ALTER TABLE `company_seal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contract_item`
--

DROP TABLE IF EXISTS `contract_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `line_no` int NOT NULL,
  `product_name` varchar(128) NOT NULL,
  `specification` varchar(256) DEFAULT NULL,
  `brand` varchar(128) DEFAULT NULL,
  `manufacturer` varchar(128) DEFAULT NULL,
  `base_unit` varchar(32) NOT NULL,
  `quantity` decimal(18,4) NOT NULL,
  `ordered_qty` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `unit_price` decimal(18,6) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `delivery_date` date DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_item_line` (`contract_id`,`line_no`),
  KEY `idx_contract_item_company` (`company_id`,`contract_id`),
  CONSTRAINT `fk_contract_item_contract` FOREIGN KEY (`contract_id`) REFERENCES `trade_contract` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_item`
--

LOCK TABLES `contract_item` WRITE;
/*!40000 ALTER TABLE `contract_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `contract_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contract_party_snapshot`
--

DROP TABLE IF EXISTS `contract_party_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_party_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_id` bigint NOT NULL,
  `company_id` bigint NOT NULL,
  `party_role` varchar(16) NOT NULL,
  `party_company_id` bigint DEFAULT NULL,
  `company_name` varchar(128) NOT NULL,
  `credit_code` varchar(32) DEFAULT NULL,
  `legal_person_name` varchar(64) DEFAULT NULL,
  `registered_address` varchar(256) DEFAULT NULL,
  `contact_phone` varchar(32) DEFAULT NULL,
  `bank_name` varchar(128) DEFAULT NULL,
  `bank_account` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_party` (`contract_id`,`party_role`),
  KEY `idx_contract_party_company` (`party_company_id`),
  CONSTRAINT `fk_contract_party_contract` FOREIGN KEY (`contract_id`) REFERENCES `trade_contract` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_party_snapshot`
--

LOCK TABLES `contract_party_snapshot` WRITE;
/*!40000 ALTER TABLE `contract_party_snapshot` DISABLE KEYS */;
/*!40000 ALTER TABLE `contract_party_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contract_template`
--

DROP TABLE IF EXISTS `contract_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `name` varchar(256) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `content` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_company` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_template`
--

LOCK TABLES `contract_template` WRITE;
/*!40000 ALTER TABLE `contract_template` DISABLE KEYS */;
INSERT INTO `contract_template` VALUES (1,1,'标准采购合同模板','采购',NULL,'2026-07-06 09:20:53','2026-07-06 09:20:53',NULL,NULL),(2,1,'框架供货协议模板','供货',NULL,'2026-07-06 09:20:53','2026-07-06 09:20:53',NULL,NULL),(3,1,'单笔交易合同模板','交易',NULL,'2026-07-06 09:20:53','2026-07-06 09:20:53',NULL,NULL),(4,1,'物流服务合同模板','物流',NULL,'2026-07-06 09:20:53','2026-07-06 09:20:53',NULL,NULL),(5,1,'测试合同123','','','2026-07-06 09:28:32','2026-07-06 09:28:32',NULL,NULL),(6,1,'标准采购合同模板','采购',NULL,'2026-07-06 09:47:43','2026-07-06 09:47:43',NULL,NULL),(7,1,'框架供货协议模板','供货',NULL,'2026-07-06 09:47:43','2026-07-06 09:47:43',NULL,NULL),(8,1,'单笔交易合同模板','交易',NULL,'2026-07-06 09:47:43','2026-07-06 09:47:43',NULL,NULL),(9,1,'物流服务合同模板','物流',NULL,'2026-07-06 09:47:43','2026-07-06 09:47:43',NULL,NULL),(10,1,'标准采购合同模板','采购',NULL,'2026-07-06 10:07:42','2026-07-06 10:07:42',NULL,NULL),(11,1,'框架供货协议模板','供货',NULL,'2026-07-06 10:07:42','2026-07-06 10:07:42',NULL,NULL),(12,1,'单笔交易合同模板','交易',NULL,'2026-07-06 10:07:42','2026-07-06 10:07:42',NULL,NULL),(13,1,'物流服务合同模板','物流',NULL,'2026-07-06 10:07:42','2026-07-06 10:07:42',NULL,NULL),(14,1,'标准采购合同模板','采购',NULL,'2026-07-07 03:19:50','2026-07-07 03:19:50',NULL,NULL),(15,1,'框架供货协议模板','供货',NULL,'2026-07-07 03:19:50','2026-07-07 03:19:50',NULL,NULL),(16,1,'单笔交易合同模板','交易',NULL,'2026-07-07 03:19:50','2026-07-07 03:19:50',NULL,NULL),(17,1,'物流服务合同模板','物流',NULL,'2026-07-07 03:19:50','2026-07-07 03:19:50',NULL,NULL),(18,1,'标准采购合同模板','采购',NULL,'2026-07-07 03:51:35','2026-07-07 03:51:35',NULL,NULL),(19,1,'框架供货协议模板','供货',NULL,'2026-07-07 03:51:35','2026-07-07 03:51:35',NULL,NULL),(20,1,'单笔交易合同模板','交易',NULL,'2026-07-07 03:51:35','2026-07-07 03:51:35',NULL,NULL),(21,1,'物流服务合同模板','物流',NULL,'2026-07-07 03:51:35','2026-07-07 03:51:35',NULL,NULL),(22,1,'标准采购合同模板','采购',NULL,'2026-07-11 13:56:25','2026-07-11 13:56:25',NULL,NULL),(23,1,'框架供货协议模板','供货',NULL,'2026-07-11 13:56:25','2026-07-11 13:56:25',NULL,NULL),(24,1,'单笔交易合同模板','交易',NULL,'2026-07-11 13:56:25','2026-07-11 13:56:25',NULL,NULL),(25,1,'物流服务合同模板','物流',NULL,'2026-07-11 13:56:25','2026-07-11 13:56:25',NULL,NULL),(26,1,'测试123','','','2026-07-11 14:04:08','2026-07-11 14:04:08',NULL,NULL),(27,1,'测试模版123123','','1、请3123\n2、123123\n3、123on哦','2026-07-11 14:04:25','2026-07-11 14:09:16',NULL,NULL),(28,1,'标准采购合同模板','采购',NULL,'2026-07-13 01:31:20','2026-07-13 01:31:20',NULL,NULL),(29,1,'框架供货协议模板','供货',NULL,'2026-07-13 01:31:20','2026-07-13 01:31:20',NULL,NULL),(30,1,'单笔交易合同模板','交易',NULL,'2026-07-13 01:31:20','2026-07-13 01:31:20',NULL,NULL),(31,1,'物流服务合同模板','物流',NULL,'2026-07-13 01:31:20','2026-07-13 01:31:20',NULL,NULL),(33,1,'123123','测试','{\"title\":\"购销合同\",\"fields\":[{\"key\":\"contractNo\",\"label\":\"合同编号\",\"value\":\"\",\"editable\":false,\"hint\":\"签订时自动生成\"},{\"key\":\"supplier\",\"label\":\"供方（甲方）\",\"value\":\"\",\"editable\":true},{\"key\":\"buyer\",\"label\":\"需方（乙方）\",\"value\":\"\",\"editable\":true},{\"key\":\"signDate\",\"label\":\"签订日期\",\"value\":\"\",\"editable\":true,\"type\":\"date\"},{\"key\":\"signPlace\",\"label\":\"签订地点\",\"value\":\"\",\"editable\":true}],\"sections\":[{\"title\":\"一、产品名称、规格、数量、单价、金额\",\"type\":\"table\",\"columns\":[\"产品名称\",\"规格型号\",\"单位\",\"数量\",\"单价(元)\",\"金额(元)\"],\"rows\":[[\"\",\"\",\"\",\"0\",\"0\",\"0\"]]},{\"title\":\"二、质量要求、技术标准\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"三、交货时间、地点、方式\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"四、运输方式及费用承担\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"五、包装标准及费用\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"七、结算方式及期限\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"八、违约责任\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"九、合同争议解决方式\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"十、合同生效与变更\",\"type\":\"clause\",\"content\":\"\"},{\"title\":\"十一、其他约定事项\",\"type\":\"clause\",\"content\":\"\"}]}','2026-07-13 03:57:36','2026-07-13 03:59:49',NULL,NULL),(34,1,'测试采购合同12111111','采购','{\"title\":\"购销合同\",\"fields\":[{\"key\":\"contractNo\",\"label\":\"合同编号\",\"value\":\"\",\"editable\":false,\"hint\":\"签订时自动生成\"},{\"key\":\"supplier\",\"label\":\"供方（甲方）\",\"value\":\"你好\",\"editable\":true},{\"key\":\"buyer\",\"label\":\"需方（乙方）\",\"value\":\"你很好\",\"editable\":true},{\"key\":\"signDate\",\"label\":\"签订日期\",\"value\":\"2026-07-13\",\"editable\":true,\"type\":\"date\"}],\"sections\":[{\"title\":\"产品名称、规格、数量、单价、金额\",\"type\":\"table\",\"columns\":[\"产品名称\",\"规格型号\",\"单位\",\"数量\",\"单价(元)\",\"金额(元)\"],\"rows\":[[\"电信\",\"1\",\"1\",\"10\",\"100\",\"1000\"],[\"网通\",\"1\",\"1\",\"30\",\"20\",\"600\"]]},{\"title\":\"质量要求、技术标准\",\"type\":\"clause\",\"content\":\"很好123\"}]}','2026-07-13 04:22:43','2026-07-13 04:22:43',NULL,NULL),(35,1,'标准采购合同模板','采购',NULL,'2026-07-13 04:30:47','2026-07-13 04:30:47',NULL,NULL),(36,1,'框架供货协议模板','供货',NULL,'2026-07-13 04:30:47','2026-07-13 04:30:47',NULL,NULL),(37,1,'单笔交易合同模板','交易',NULL,'2026-07-13 04:30:47','2026-07-13 04:30:47',NULL,NULL),(40,1,'标准采购合同模板','采购',NULL,'2026-07-13 09:17:21','2026-07-13 09:17:21',NULL,NULL),(41,1,'框架供货协议模板','供货',NULL,'2026-07-13 09:17:21','2026-07-13 09:17:21',NULL,NULL),(42,1,'单笔交易合同模板','交易',NULL,'2026-07-13 09:17:21','2026-07-13 09:17:21',NULL,NULL),(43,1,'物流服务合同模板','物流',NULL,'2026-07-13 09:17:21','2026-07-13 09:17:21',NULL,NULL),(44,1,'标准采购合同模板','采购',NULL,'2026-07-14 11:14:45','2026-07-14 11:14:45',NULL,NULL),(45,1,'框架供货协议模板','供货',NULL,'2026-07-14 11:14:45','2026-07-14 11:14:45',NULL,NULL),(46,1,'单笔交易合同模板','交易',NULL,'2026-07-14 11:14:45','2026-07-14 11:14:45',NULL,NULL),(47,1,'物流服务合同模板','物流',NULL,'2026-07-14 11:14:45','2026-07-14 11:14:45',NULL,NULL);
/*!40000 ALTER TABLE `contract_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `counterparty_relation`
--

DROP TABLE IF EXISTS `counterparty_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counterparty_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `counterparty_company_id` bigint DEFAULT NULL,
  `counterparty_company_name` varchar(128) NOT NULL,
  `relation_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counterparty_relation`
--

LOCK TABLES `counterparty_relation` WRITE;
/*!40000 ALTER TABLE `counterparty_relation` DISABLE KEYS */;
INSERT INTO `counterparty_relation` VALUES (1,1,NULL,'河北通瑞贸易有限公司','SUPPLIER','ACTIVE','2026-06-29 07:47:59'),(2,1,NULL,'河北逸泽昌贸易有限公司','SUPPLIER','ACTIVE','2026-06-29 07:47:59'),(3,2,NULL,'上海浦发贸易有限公司','SUPPLIER','ACTIVE','2026-06-29 07:47:59'),(4,2,NULL,'浙江义乌商贸有限公司','SUPPLIER','ACTIVE','2026-06-29 07:47:59'),(5,2,NULL,'江苏南通纺织品有限公司','SUPPLIER','ACTIVE','2026-06-29 07:47:59');
/*!40000 ALTER TABLE `counterparty_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'0','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'tradepass','2026-07-15 06:40:13',0,1),(2,'1','baseline schema','SQL','V1__baseline_schema.sql',489984491,'tradepass','2026-07-15 06:40:13',24,1),(3,'2','security and tenant boundaries','SQL','V2__security_and_tenant_boundaries.sql',-714214828,'tradepass','2026-07-15 06:40:13',82,1),(4,'3','complete trade role permissions','SQL','V3__complete_trade_role_permissions.sql',679698359,'tradepass','2026-07-15 06:40:13',15,1),(5,'4','trade fulfillment and audit','SQL','V4__trade_fulfillment_and_audit.sql',-1789749035,'tradepass','2026-07-17 08:05:57',230,0);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `perm_def`
--

DROP TABLE IF EXISTS `perm_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perm_def` (
  `code` varchar(64) NOT NULL,
  `label` varchar(64) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `perm_def`
--

LOCK TABLES `perm_def` WRITE;
/*!40000 ALTER TABLE `perm_def` DISABLE KEYS */;
INSERT INTO `perm_def` VALUES ('auth_manage','授权管理',13),('buyer_view','需方首页',2),('company_manage','企业认证',14),('contract_sign','签订合同',7),('contract_template','合同模板管理',6),('contract_view','合同预览',8),('counterparty_manage','供方公司管理',3),('inventory_view','库存查看',11),('invoice_view','发票查看',9),('member_manage','成员管理',12),('order_create','下单',5),('order_view','订单查看',4),('reconciliation','对账情况',10),('seal_manage','电子章管理',15),('supplier_view','供方首页',1);
/*!40000 ALTER TABLE `perm_def` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ranking_snapshot`
--

DROP TABLE IF EXISTS `ranking_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ranking_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `ranking_type` varchar(32) NOT NULL,
  `period_type` varchar(32) NOT NULL,
  `counterparty_name` varchar(128) NOT NULL,
  `rank_no` int NOT NULL,
  `total_amount` decimal(18,2) NOT NULL,
  `order_count` int NOT NULL,
  `generated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ranking_query` (`company_id`,`ranking_type`,`period_type`,`rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ranking_snapshot`
--

LOCK TABLES `ranking_snapshot` WRITE;
/*!40000 ALTER TABLE `ranking_snapshot` DISABLE KEYS */;
/*!40000 ALTER TABLE `ranking_snapshot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `real_name_verification`
--

DROP TABLE IF EXISTS `real_name_verification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `real_name_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `verification_type` varchar(32) NOT NULL,
  `provider` varchar(64) NOT NULL,
  `provider_flow_no` varchar(128) DEFAULT NULL,
  `result_status` varchar(32) NOT NULL,
  `result_snapshot` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `real_name_verification`
--

LOCK TABLES `real_name_verification` WRITE;
/*!40000 ALTER TABLE `real_name_verification` DISABLE KEYS */;
/*!40000 ALTER TABLE `real_name_verification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_def`
--

DROP TABLE IF EXISTS `role_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_def` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `code` varchar(64) DEFAULT NULL,
  `name` varchar(64) NOT NULL,
  `permissions` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_role` (`company_id`,`name`),
  UNIQUE KEY `uk_company_role_code` (`company_id`,`code`)
) ENGINE=InnoDB AUTO_INCREMENT=441 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_def`
--

LOCK TABLES `role_def` WRITE;
/*!40000 ALTER TABLE `role_def` DISABLE KEYS */;
INSERT INTO `role_def` VALUES (1,1,'LEGAL','法人','[\"all\"]','2026-06-23 11:34:40'),(2,1,'ADMIN','管理员','[\"member_manage\", \"auth_manage\", \"company_manage\", \"seal_manage\"]','2026-06-23 11:34:40'),(3,1,'SALES','销售员','[\"supplier_view\", \"counterparty_manage\", \"order_view\", \"contract_sign\", \"contract_view\", \"reconciliation\"]','2026-06-23 11:34:40'),(4,1,'PURCHASER','采购员','[\"buyer_view\", \"order_create\", \"contract_view\", \"order_view\", \"contract_sign\", \"reconciliation\"]','2026-06-23 11:34:40'),(5,1,'FINANCE','财务','[\"invoice_view\", \"reconciliation\"]','2026-06-23 11:34:40'),(6,2,'LEGAL','法人','[\"all\"]','2026-06-23 11:34:40'),(7,2,'ADMIN','管理员','[\"member_manage\", \"auth_manage\", \"company_manage\"]','2026-06-23 11:34:40'),(8,2,'SALES','销售员','[\"supplier_view\", \"counterparty_manage\", \"order_view\", \"contract_sign\", \"contract_view\", \"reconciliation\"]','2026-06-23 11:34:40'),(9,2,'PURCHASER','采购员','[\"buyer_view\", \"order_create\", \"contract_view\", \"order_view\", \"contract_sign\", \"reconciliation\"]','2026-06-23 11:34:40'),(10,2,'FINANCE','财务','[\"invoice_view\", \"reconciliation\"]','2026-06-23 11:34:40');
/*!40000 ALTER TABLE `role_def` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `openid` (`openid`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'dev-openid-001','18800000001','满帅','ACTIVE','2026-06-22 03:30:43','2026-06-22 03:30:43'),(2,'dev-openid-002','18800000002','张采购','ACTIVE','2026-06-22 03:30:43','2026-06-22 03:30:43'),(3,'dev-openid-003','18800000003','李销售','ACTIVE','2026-06-22 03:30:43','2026-06-22 03:30:43'),(4,'dev-openid-004','18800000004','王财务','ACTIVE','2026-06-22 03:30:43','2026-06-22 03:30:43'),(5,'dev-openid-005','18800000005','赵管理','ACTIVE','2026-06-22 03:30:43','2026-06-22 03:30:43'),(6,'dev-phone-18800000006','18800000006','员工0006','ACTIVE','2026-06-22 03:42:27','2026-06-22 03:42:27'),(7,'dev-new-user-007','','新用户','ACTIVE','2026-06-22 03:58:16','2026-06-22 03:58:16'),(8,'dev-new-user-008','','新用户','ACTIVE','2026-06-22 03:58:49','2026-06-22 03:58:49'),(9,'dev-new-user','','新用户','ACTIVE','2026-06-22 11:14:23','2026-06-22 11:14:23'),(10,'dev-test','','新用户','ACTIVE','2026-06-22 11:25:23','2026-06-22 11:25:23'),(11,'dev-test-001','','测试用户','ACTIVE','2026-06-26 03:04:16','2026-06-26 03:04:16'),(12,'dev-phone-0001','18800000001','微信用户','ACTIVE','2026-07-14 11:22:58','2026-07-14 11:22:58'),(13,'dev-phone-1234','18812341234','微信用户','ACTIVE','2026-07-14 11:22:58','2026-07-14 11:22:58');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `template_category`
--

DROP TABLE IF EXISTS `template_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_cat` (`company_id`,`name`),
  KEY `idx_company` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `template_category`
--

LOCK TABLES `template_category` WRITE;
/*!40000 ALTER TABLE `template_category` DISABLE KEYS */;
INSERT INTO `template_category` VALUES (1,1,'采购',1),(2,1,'供货',2),(3,1,'交易',3),(4,1,'物流',4),(5,1,'服务',5),(6,1,'其他',6),(25,1,'测试',0),(26,1,'测试123123',0);
/*!40000 ALTER TABLE `template_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_contract`
--

DROP TABLE IF EXISTS `trade_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_no` varchar(64) NOT NULL,
  `company_id` bigint NOT NULL,
  `counterparty_company_id` bigint DEFAULT NULL,
  `counterparty_name` varchar(128) NOT NULL,
  `direction` varchar(16) NOT NULL DEFAULT 'SALE',
  `client_request_id` varchar(64) DEFAULT NULL,
  `name` varchar(256) NOT NULL,
  `template_name` varchar(128) DEFAULT NULL,
  `amount` decimal(15,2) NOT NULL DEFAULT '0.00',
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `terms` text,
  `version_no` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `initiated_by` bigint NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_no` (`company_id`,`contract_no`),
  UNIQUE KEY `uk_contract_request` (`company_id`,`client_request_id`),
  KEY `idx_company` (`company_id`),
  KEY `idx_counterparty` (`counterparty_name`),
  KEY `idx_contract_counterparty_company` (`counterparty_company_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_contract`
--

LOCK TABLES `trade_contract` WRITE;
/*!40000 ALTER TABLE `trade_contract` DISABLE KEYS */;
INSERT INTO `trade_contract` VALUES (1,'HT-LEGACY-00000001',1,NULL,'河北通瑞贸易有限公司','SALE',NULL,'年度采购框架协议','标准采购合同模板',500000.00,'2026-01-01','2026-12-31',NULL,1,'ACTIVE',1,NULL,NULL,'2026-07-06 08:39:59','2026-07-17 08:05:57'),(2,'HT-LEGACY-00000002',2,NULL,'上海浦发贸易有限公司','SALE',NULL,'年度销售代理合同','标准采购合同模板',320000.00,'2026-02-01','2027-01-31',NULL,1,'PENDING',2,NULL,NULL,'2026-07-06 08:39:59','2026-07-17 08:05:57'),(3,'HT-LEGACY-00000003',1,NULL,'河北逸泽昌贸易有限公司','SALE',NULL,'测试合同-待审批','标准采购合同模板',99999.00,'2026-07-06','2026-12-31','测试条款',1,'PENDING',1,NULL,NULL,'2026-07-06 08:41:31','2026-07-17 08:05:57'),(4,'HT-LEGACY-00000004',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-06 08:42:34','2026-07-17 08:05:57'),(5,'HT-LEGACY-00000005',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-06 09:20:31','2026-07-17 08:05:57'),(6,'HT-LEGACY-00000006',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-06 09:20:53','2026-07-17 08:05:57'),(7,'HT-LEGACY-00000007',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-06 09:47:43','2026-07-17 08:05:57'),(8,'HT-LEGACY-00000008',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-06 10:07:42','2026-07-17 08:05:57'),(9,'HT-LEGACY-00000009',1,NULL,'河北通瑞贸易有限公司','SALE',NULL,'测试合同','单笔交易合同模板',10000.00,'2026-07-07','2026-09-07','阿萨德',1,'PENDING',1,NULL,NULL,'2026-07-07 02:14:16','2026-07-17 08:05:57'),(10,'HT-LEGACY-00000010',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-07 03:19:50','2026-07-17 08:05:57'),(11,'HT-LEGACY-00000011',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-07 03:51:35','2026-07-17 08:05:57'),(12,'HT-LEGACY-00000012',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-11 13:56:25','2026-07-17 08:05:57'),(13,'HT-LEGACY-00000013',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-13 01:31:20','2026-07-17 08:05:57'),(14,'HT-LEGACY-00000014',1,NULL,'test','SALE',NULL,'test','test',100.00,'2026-07-13','2026-12-31','{}',1,'PENDING',1,NULL,NULL,'2026-07-13 04:26:47','2026-07-17 08:05:57'),(15,'HT-LEGACY-00000015',1,NULL,'test','SALE',NULL,'test','test',100.00,'2026-07-13','2026-12-31','test',1,'PENDING',1,NULL,NULL,'2026-07-13 04:27:08','2026-07-17 08:05:57'),(16,'HT-LEGACY-00000016',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-13 04:30:47','2026-07-17 08:05:57'),(17,'HT-LEGACY-00000017',1,NULL,'test3','SALE',NULL,'test3','test',100.00,'2026-07-13',NULL,'{}',1,'PENDING',1,NULL,NULL,'2026-07-13 04:30:54','2026-07-17 08:05:57'),(18,'HT-LEGACY-00000018',1,NULL,'河北通瑞贸易有限公司','SALE',NULL,'购销合同','测试采购合同12111111',1600.00,'2026-07-13',NULL,'{\"title\":\"购销合同\",\"fields\":[{\"key\":\"contractNo\",\"label\":\"合同编号\",\"value\":\"\",\"editable\":false,\"hint\":\"签订时自动生成\"},{\"key\":\"supplier\",\"label\":\"供方（甲方）\",\"value\":\"河北通瑞贸易有限公司\",\"editable\":true},{\"key\":\"buyer\",\"label\":\"需方（乙方）\",\"value\":\"河北光屿行贸易有限公司\",\"editable\":true},{\"key\":\"signDate\",\"label\":\"签订日期\",\"value\":\"2026-07-13\",\"editable\":true,\"type\":\"date\"}],\"sections\":[{\"title\":\"产品名称、规格、数量、单价、金额\",\"type\":\"table\",\"columns\":[\"产品名称\",\"规格型号\",\"单位\",\"数量\",\"单价(元)\",\"金额(元)\"],\"rows\":[[\"电信\",\"1\",\"1\",\"10\",\"100\",\"1000\"],[\"网通\",\"1\",\"1\",\"30\",\"20\",\"600\"]],\"summary\":{\"totalAmount\":\"1600\",\"totalAmountCn\":\"壹仟陆佰元整\"}},{\"title\":\"质量要求、技术标准\",\"type\":\"clause\",\"content\":\"很好123\"}]}',1,'PENDING',1,NULL,NULL,'2026-07-13 07:15:56','2026-07-17 08:05:57'),(19,'HT-LEGACY-00000019',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-13 09:17:21','2026-07-17 08:05:57'),(20,'HT-LEGACY-00000020',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:14:45','2026-07-17 08:05:57'),(21,'HT-LEGACY-00000021',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:16:59','2026-07-17 08:05:57'),(22,'HT-LEGACY-00000022',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:21:09','2026-07-17 08:05:57'),(23,'HT-LEGACY-00000023',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:22:34','2026-07-17 08:05:57'),(24,'HT-LEGACY-00000024',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:23:35','2026-07-17 08:05:57'),(25,'HT-LEGACY-00000025',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:24:50','2026-07-17 08:05:57'),(26,'HT-LEGACY-00000026',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:31:36','2026-07-17 08:05:57'),(27,'HT-LEGACY-00000027',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-14 11:32:58','2026-07-17 08:05:57'),(28,'HT-LEGACY-00000028',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-15 06:40:13','2026-07-17 08:05:57'),(29,'HT-LEGACY-00000029',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-15 06:43:39','2026-07-17 08:05:57'),(30,'HT-LEGACY-00000030',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-17 01:18:37','2026-07-17 08:05:57'),(31,'HT-LEGACY-00000031',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-17 01:19:04','2026-07-17 08:05:57'),(32,'HT-LEGACY-00000032',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-17 05:47:38','2026-07-17 08:05:57'),(33,'HT-LEGACY-00000033',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-17 05:48:04','2026-07-17 08:05:57'),(34,'HT-LEGACY-00000034',2,NULL,'河北光广贸易有限公司','SALE',NULL,'年度物流服务合同','物流服务合同模板',150000.00,'2026-03-01','2027-02-28',NULL,1,'PENDING',3,NULL,NULL,'2026-07-17 05:51:00','2026-07-17 08:05:57');
/*!40000 ALTER TABLE `trade_contract` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_order`
--

DROP TABLE IF EXISTS `trade_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `contract_id` bigint DEFAULT NULL,
  `counterparty_company_id` bigint DEFAULT NULL,
  `direction` varchar(32) NOT NULL,
  `counterparty_name` varchar(128) NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `client_request_id` varchar(64) DEFAULT NULL,
  `amount` decimal(18,2) NOT NULL,
  `order_date` date NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'CONFIRMED',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_no` (`order_no`),
  UNIQUE KEY `uk_order_no` (`company_id`,`order_no`),
  UNIQUE KEY `uk_order_request` (`company_id`,`client_request_id`),
  KEY `idx_company_direction_date` (`company_id`,`direction`,`order_date`),
  KEY `idx_order_contract` (`company_id`,`contract_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_order`
--

LOCK TABLES `trade_order` WRITE;
/*!40000 ALTER TABLE `trade_order` DISABLE KEYS */;
INSERT INTO `trade_order` VALUES (1,1,NULL,NULL,'SALE','河北通瑞贸易有限公司','ORD-001',NULL,1280000.00,'2026-06-26','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(2,1,NULL,NULL,'SALE','河北逸泽昌贸易有限公司','ORD-002',NULL,860000.00,'2026-04-29','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(3,1,NULL,NULL,'SALE','河北佰盛电缆科技有限公司','ORD-003',NULL,620000.00,'2025-06-29','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(4,1,NULL,NULL,'PURCHASE','河北通瑞贸易有限公司','ORD-004',NULL,960000.00,'2026-06-24','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(5,1,NULL,NULL,'PURCHASE','河北逸泽昌贸易有限公司','ORD-005',NULL,710000.00,'2026-05-29','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(6,2,NULL,NULL,'SALE','上海浦发贸易有限公司','ORD-006',NULL,2560000.00,'2026-06-27','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(7,2,NULL,NULL,'SALE','浙江义乌商贸有限公司','ORD-007',NULL,1890000.00,'2026-06-22','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(8,2,NULL,NULL,'SALE','江苏南通纺织品有限公司','ORD-008',NULL,1430000.00,'2026-05-29','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(9,2,NULL,NULL,'PURCHASE','上海浦发贸易有限公司','ORD-009',NULL,980000.00,'2026-06-19','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57'),(10,2,NULL,NULL,'PURCHASE','浙江义乌商贸有限公司','ORD-010',NULL,810000.00,'2026-05-29','CONFIRMED',NULL,'2026-06-29 07:50:24','2026-07-17 08:05:57');
/*!40000 ALTER TABLE `trade_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_order_item`
--

DROP TABLE IF EXISTS `trade_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_name` varchar(128) NOT NULL,
  `quantity` decimal(18,4) NOT NULL,
  `unit_price` decimal(18,4) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_order_item`
--

LOCK TABLES `trade_order_item` WRITE;
/*!40000 ALTER TABLE `trade_order_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `trade_stat_daily`
--

DROP TABLE IF EXISTS `trade_stat_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trade_stat_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `direction` varchar(32) NOT NULL,
  `counterparty_name` varchar(128) NOT NULL,
  `stat_date` date NOT NULL,
  `order_count` int NOT NULL,
  `total_amount` decimal(18,2) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_daily` (`company_id`,`direction`,`counterparty_name`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trade_stat_daily`
--

LOCK TABLES `trade_stat_daily` WRITE;
/*!40000 ALTER TABLE `trade_stat_daily` DISABLE KEYS */;
/*!40000 ALTER TABLE `trade_stat_daily` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'tradepass'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-17  9:00:59
