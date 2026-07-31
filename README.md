# 财源通天 (TradePass)

面向企业贸易、合同签署与企业协作场景的微信小程序。项目采用 Java 后端 + 原生微信小程序前端，当前以 MVP 单体应用方式快速迭代。

## 技术栈

- **后端**：Java 17 · Spring Boot 3.3.6 · Spring Web · MyBatis-Plus · MySQL 8.4 · Redis 7 · 阿里云 OSS
- **小程序**：原生微信小程序 · WXML/WXSS/JS
- **基础设施**：Docker Compose（MySQL、Redis）· 阿里云 OSS 私有 Bucket

## 项目结构

```
backend/        Spring Boot 单体应用（端口 9999）
miniprogram/    微信小程序
docs/           产品与开发文档
scripts/        启动/关闭脚本
```

后端入口：`backend/src/main/java/com/tradepass/TradepassApplication.java`

小程序入口：`miniprogram/app.js`、`miniprogram/app.json`

## 快速开始

```bash
# 一键启动（MySQL + Redis + 应用）
./scripts/start.sh

# 关闭
./scripts/stop.sh
```

IDEA 开发：直接 Run `backend/.../TradepassApplication.java`

API 地址：`http://localhost:9999/api`

后端单独构建：

```bash
cd backend
mvn -DskipTests package
```

## 测试与覆盖率

```bash
# 后端单元测试、HTTP 契约测试、打包及 JaCoCo 覆盖率门禁
mvn verify

# 小程序工具、请求封装、共享组件与应用会话测试
npm test
```

后端覆盖率报告生成在 `backend/target/site/jacoco/index.html`。构建要求后端整体行覆盖率不低于 60%，分支覆盖率不低于 55%。

## 小程序

微信开发者工具打开 `miniprogram/` 目录。开发者工具模拟器会请求本地 API：`http://localhost:9999/api`；真机和线上默认请求 `app.js` 中配置的云托管 API。

Tab：

- `pages/index/index`：首页
- `pages/company/company`：企业
- `pages/me/me`：我的

主要页面：

- 登录与隐私：登录、手机号登录、隐私协议
- 企业：企业首页、企业绑定、企业认证
- 权限：授权管理、角色管理
- 贸易：供方关系、订单详情、排行
- 合同：合同模板、模板详情、发起签约、合同预览、合同审批、对账

## 功能

- 微信登录、手机号绑定、开发环境用户切换
- 多企业、多角色、细粒度权限点
- 企业认证流程：工商信息、实名、人脸、电子章；正式环境需接入认证与存储供应商
- 邀请码加入企业、供方公司邀请绑定
- 授权审批、自定义角色、权限勾选
- 供方/需方首页排行，支持总、年、月维度
- 订单、供方关系、合同模板、发起签约、合同审批、对账

## 后端接口概览

- `AuthController`：登录、手机号绑定、当前用户、企业切换、待办、开发用户切换
- `CompanyController`：企业查询/提交/认证、邀请码、成员授权、自定义角色
- `TradeController`：订单、供方关系、合同模板分类、合同模板、合同发起与审批
- `RankingController`：供方/需方首页、销售/采购排行
- `FileController`：开发环境上传凭证占位接口；生产环境未配置对象存储时拒绝请求

后端按常规分层组织：

- `controller`：接口入参与响应包装
- `service`：业务编排、权限校验、事务边界
- `mapper`：MyBatis-Plus `BaseMapper` 与必要的查询 SQL
- `entity`：数据库表实体
- `dto/request`、`dto/response`：请求与响应模型

接口统一返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

请求认证：

- 登录成功后签发高熵随机 token；数据库仅保存 token 的 SHA-256 摘要，并支持服务端注销
- 受保护接口通过 `Authorization` 请求头传 token
- 当前操作企业通过 `X-Company-Id` 请求头传递

## 数据库与初始化

当前 MVP 阶段由后端启动时自动创建表并写入演示数据，逻辑位于 `DatabaseInitializer`。MySQL 和 Redis 默认由 Docker Compose 提供：

- MySQL：`localhost:1118`
- database：`tradepass`
- username：`tradepass`
- password：`tradepass_pwd`
- Redis：`localhost:1119`

Redis 用于短时鉴权缓存、企业搜索限流、微信 `access_token` 共享缓存和首页排行缓存。MySQL 仍是业务数据与登录会话的权威数据源；Redis 不可用时应用会自动回退到数据库或单实例内存实现。

合同、附件和单据采用“OSS 二进制 + MySQL 元数据”模式：已签署生效合同的冻结 PDF、物流图片、合同附件/转款凭证、XLSX 对账单保存到私有 OSS；MySQL 保存业务归属、对象 key/versionId、文件大小、SHA-256、ETag 和服务端加密方式。每次下载都经过后端权限校验，并重新校验文件长度和 SHA-256。开发环境未启用 OSS 时保留历史 BLOB 兼容路径；`prod` profile 会强制要求 OSS，避免上线后误存数据库。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` | `localhost` | 数据库主机 |
| `DB_PORT` | `1118` | 数据库端口 |
| `DB_USERNAME` | `tradepass` | 数据库用户 |
| `DB_PASSWORD` | `tradepass_pwd` | 数据库密码 |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `1119` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `TRADEPASS_REDIS_ENABLED` | `true` | 是否启用 Redis 缓存与分布式限流 |
| `TRADEPASS_REDIS_KEY_PREFIX` | `tradepass` | Redis 键前缀 |
| `TRADEPASS_OSS_ENABLED` | `false` | 是否启用阿里云 OSS；上线必须为 `true` |
| `TRADEPASS_OSS_REQUIRED` | `false` | OSS 未配置时是否拒绝启动；`prod` profile 固定为 `true` |
| `OSS_ENDPOINT` | 空 | OSS HTTPS Endpoint，例如 `oss-cn-hangzhou.aliyuncs.com` |
| `OSS_BUCKET` | 空 | 私有 Bucket 名称 |
| `OSS_ACCESS_KEY_ID` | 空 | RAM 用户/角色 AccessKey ID，禁止使用主账号密钥 |
| `OSS_ACCESS_KEY_SECRET` | 空 | RAM 用户/角色 AccessKey Secret，不得提交到 Git |
| `OSS_SECURITY_TOKEN` | 空 | 使用 STS 临时凭证时填写 |
| `OSS_SERVER_SIDE_ENCRYPTION` | `AES256` | 服务端加密算法：`AES256` 或 `KMS` |
| `OSS_KMS_KEY_ID` | 空 | 使用 `KMS` 时可指定 CMK ID |
| `TRADEPASS_OSS_KEY_PREFIX` | `tradepass` | OSS 对象 key 前缀；生产环境设置为 `prod` |
| `TRADEPASS_OSS_MIGRATE_LEGACY_BLOBS` | `false` | 启动时迁移历史合同/文件；完成后恢复为 `false` |
| `WECHAT_APP_ID` | `wxd6d1e93a3868253e` | 小程序 AppID |
| `WECHAT_APP_SECRET` | 空 | 小程序密钥 |
| `TRADEPASS_DEV_ENABLED` | `false` | 是否启用 `/api/dev/**` 和开发占位能力；`dev` profile 会开启 |
| `TRADEPASS_VERIFICATION_AUTO_APPROVE` | `false` | 是否允许开发环境模拟认证结果，生产环境应保持关闭 |

### 阿里云 OSS 上线配置

1. 创建与应用同地域的 Bucket，读写权限设为“私有”，开启阻止公共访问；合同证据建议另外开启版本控制和合规保留（WORM）。
2. 创建最小权限 RAM 用户或角色，仅授予目标 Bucket 前缀的读写权限；使用 KMS 时再授予对应密钥的加解密权限。
3. `cp .env.example .env` 后在本机填写变量。启动脚本会读取 `.env`，该文件已被 Git 忽略。
4. 首次切换时把 `TRADEPASS_OSS_MIGRATE_LEGACY_BLOBS=true` 启动一次。系统会先上传并校验，再清空原 BLOB；失败记录保留在 MySQL，可安全重试。确认迁移日志成功后改回 `false`。

新签署合同会在状态变为 `ACTIVE` 的同一业务操作中生成一次 PDF 并归档；后续下载始终按 MySQL 记录的 OSS `versionId` 读取，不再重新生成。历史生效合同会在迁移步骤中补做冻结归档。

生产对象统一使用以下层级：生效合同为 `prod/contract/{公司ID}/{合同ID}/v{版本号}/{SHA256}.pdf`；物流图片、转款凭证和其他附件为 `prod/file/{公司ID}/{合同ID}/{文件类型}/{年}/{月}/{UUID}-{SHA256}.{后缀}`；对账单由于不绑定单份合同，使用 `prod/file/{公司ID}/reconciliation/{对方公司ID}/{YYYY-MM}/{UUID}-{SHA256}.xlsx`。

## 开发说明

- 后端目前是单体应用，业务读写通过 MyBatis-Plus Mapper 操作数据库。
- 通用响应 DTO 在 `backend/src/main/java/com/tradepass/common/TradePassDtos.java`，接口请求/响应模型在 `backend/src/main/java/com/tradepass/dto/`。
- 小程序请求封装在 `miniprogram/utils/request.js`，会自动注入 token 和当前企业 ID。
- 开发接口仅在 `TRADEPASS_DEV_ENABLED=true` 时可用，生产环境会隐藏 `/api/dev/**`。
