# 财源通天 (TradePass)

面向企业贸易、合同签署与企业协作场景的微信小程序。项目采用 Java 后端 + 原生微信小程序前端，当前以 MVP 单体应用方式快速迭代。

## 技术栈

- **后端**：Java 17 · Spring Boot 3.3.6 · Spring Web · MyBatis-Plus · MySQL 8.4 · 微信云托管对象存储
- **小程序**：原生微信小程序 · WXML/WXSS/JS
- **基础设施**：Docker Compose（MySQL；Redis 仅作可选 profile）· CloudBase/COS 私有对象存储

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
# 一键启动（MySQL + 应用；Redis 可选）
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

所有环境的表结构由后端启动时的 Flyway 迁移管理。`DatabaseInitializer` 的演示数据仅在本地 `dev` 配置启用；`prod` 关闭演示数据和体验手机号特权初始化。新企业走正常认证流程，认证通过后由 `TenantBootstrapService` 创建该企业的标准角色与模板。MySQL 默认由 Docker Compose 提供：

- MySQL：`localhost:1118`
- database：`tradepass`
- username：`tradepass`
- password：`tradepass_pwd`

从 V26 起，39 张业务表的主键由应用生成 Snowflake ID，数据库不再使用 `AUTO_INCREMENT`；MyBatis 和 JDBC 共用一个 ID 生成器。接口中的 `Long/long` 值按字符串传递，小程序的 ID 全程使用字符串。`SystemPermissionInitializer` 在启动时幂等补齐 18 项权限定义，不创建业务账号或企业。

需要清空数据重新验收时，使用 [完整重置说明](docs/database-reset-and-ids.md) 和 [清表 SQL](scripts/reset-for-online-retest.sql)。新版可以从空库重新建表及初始化，无须保留旧自增值或权限字典。后端与小程序需要同步更新。

Redis 默认关闭且不是运行必需项：登录会话与排行榜直接查询 MySQL，企业搜索限流和微信 `access_token` 使用单实例内存。如未来多实例部署后需要共享缓存或分布式限流，可执行 `docker compose --profile redis up -d` 启动本地 Redis，并显式设置 `TRADEPASS_REDIS_ENABLED=true`。

合同、附件和单据采用“微信云托管对象存储二进制 + MySQL 元数据”模式：已签署生效合同的冻结 PDF、物流图片、合同附件、转款凭证、发票和历史 XLSX 对账单保存到私有对象存储；MySQL 保存业务归属、确认状态、结构化金额、对象 key、文件大小、SHA-256 和服务端加密方式。销售单、转款凭证和发票经对方企业确认后，自动进入每对合作企业唯一的对账台账。后端通过开放接口服务取得短期 COS 凭证，不保存永久 SecretId/SecretKey；每次上传后及下载时都会重新校验文件长度和 SHA-256。开发环境未启用云存储时保留历史 BLOB 兼容路径；`prod` profile 会强制要求云存储，避免上线后误存数据库。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` | `localhost` | 数据库主机 |
| `DB_PORT` | `1118` | 数据库端口 |
| `DB_USERNAME` | `tradepass` | 数据库用户 |
| `DB_PASSWORD` | `tradepass_pwd` | 数据库密码 |
| `TRADEPASS_IDS_WORKER_ID` | 自动推导 | Snowflake worker 编号，范围 0–31；须与 datacenter 编号一起配置 |
| `TRADEPASS_IDS_DATACENTER_ID` | 自动推导 | Snowflake datacenter 编号，范围 0–31；多实例须给每个并行实例分配不同的编号组合 |
| `TRADEPASS_REDIS_ENABLED` | `false` | 是否启用可选的 Redis 缓存与分布式限流 |
| `REDIS_HOST` | `localhost` | 仅启用 Redis 时需要：Redis 主机 |
| `REDIS_PORT` | `1119` | 仅启用 Redis 时需要：Redis 端口 |
| `REDIS_PASSWORD` | 空 | 仅启用 Redis 时需要：Redis 密码 |
| `TRADEPASS_REDIS_KEY_PREFIX` | `tradepass` | 仅启用 Redis 时需要：Redis 键前缀 |
| `TRADEPASS_STORAGE_ENABLED` | 开发环境 `false`，生产环境 `true` | 是否启用微信云托管对象存储；通常无需在云托管中另配 |
| `TRADEPASS_STORAGE_REQUIRED` | 开发环境 `false`，生产环境固定 `true` | 云存储未配置时是否拒绝启动 |
| `CLOUDBASE_STORAGE_BUCKET` | 生产环境 `7072-prod-d7g9zrn5s7e6aab68-1446724178` | 云托管对象存储 Bucket；仅在更换 Bucket 时覆盖 |
| `CLOUDBASE_STORAGE_REGION` | `ap-shanghai` | 对象存储地域；应与控制台“存储配置”一致 |
| `TRADEPASS_STORAGE_KEY_PREFIX` | 开发环境 `tradepass`，生产环境 `prod` | 对象 key 前缀；通常无需在云托管中另配 |
| `TRADEPASS_STORAGE_MIGRATE_LEGACY_BLOBS` | `false` | 启动时迁移历史合同/文件；完成后恢复为 `false` |
| `WECHAT_APP_ID` | `wxd6d1e93a3868253e` | 小程序 AppID |
| `WECHAT_CLOUD_OPEN_API_ENABLED` | 开发环境 `false`，生产环境 `true` | 是否使用云托管开放接口服务免鉴权调用微信接口 |
| `WECHAT_APP_SECRET` | 空 | 仅本地传统 `code2Session`/`access_token` 兼容路径需要；生产云调用无需配置 |
| `TRADEPASS_DEV_ENABLED` | `false` | 是否启用 `/api/dev/**` 和开发占位能力；`dev` profile 会开启 |
| `TRADEPASS_EXPERIENCE_TEST_ACCOUNTS_ENABLED` | 基础配置 `false`；`prod` 固定关闭 | 原体验手机号自动创建企业、授予法人和模拟认证状态的开关；生产配置不再读取此变量，旧值为 `true` 也不会启用；本地 `dev` 配置仍保留体验能力 |
| `TRADEPASS_CA_MOCK_ENABLED` | `false` | 是否启用 CA、实名、人脸和电子章模拟结果；真实业务保持关闭，本地 `dev` 配置会开启 |

### 微信云托管对象存储上线配置

1. 在云托管对象存储的“存储权限”中设为“仅管理员可读写”。
2. 在“对象存储 → 存储配置”确认 Bucket 和 Region 与生产配置一致。
3. 在“服务管理 → 云调用”开启“开放接口服务”，并在云调用权限中添加 `/wxa/business/getuserphonenumber`。对象存储使用开放接口服务集成的专用接口集合，代码会调用 `/_/cos/getauth` 和 `/_/cos/metaid/encode`；不用在普通“微信令牌权限”列表中选择 `/tcb/uploadfile`。
4. 开启开放接口服务后必须重新构建并发布服务版本；开关不会补充到已经存在的旧版本。
5. 生产镜像已经启用 `prod` profile；Bucket、Region、`prod` 前缀和“必须启用对象存储”均有生产默认值。应用通过开放接口获取临时凭证，不需要配置永久 SecretId/SecretKey。
6. 如需把数据库中的历史 BLOB 转入对象存储，可临时设置 `TRADEPASS_STORAGE_MIGRATE_LEGACY_BLOBS=true` 发布一次，确认日志成功后立即恢复为 `false`；此开关与阿里 OSS 无关。


新签署合同会在状态变为 `ACTIVE` 的同一业务操作中生成一次 PDF 并归档；对象 key 包含合同版本和 SHA-256，不允许不同内容覆盖。后续下载会重新校验文件长度和 SHA-256，不再重新生成。历史生效合同会在迁移步骤中补做冻结归档。

生产对象统一使用以下层级：生效合同为 `prod/contract/{公司ID}/{合同ID}/v{版本号}/{SHA256}.pdf`；物流图片、转款凭证和其他附件为 `prod/file/{公司ID}/{合同ID}/{文件类型}/{年}/{月}/{UUID}-{SHA256}.{后缀}`；对账单由于不绑定单份合同，使用 `prod/file/{公司ID}/reconciliation/{对方公司ID}/{YYYY-MM}/{UUID}-{SHA256}.xlsx`。

### 微信登录与手机号云调用

体验版和正式版通过 `wx.cloud.callContainer` 访问 `tradepass` 服务，微信链路会自动注入可信的 `x-wx-openid`。后端直接使用该身份创建业务会话，不再调用 `auth.code2Session`。手机号按钮产生的一次性 `phoneCode` 发送到后端后，后端通过 `http://api.weixin.qq.com/wxa/business/getuserphonenumber` 免 `access_token` 换取手机号。

小程序端的云环境配置位于 `miniprogram/app.js`，当前环境为 `prod-d7g9zrn5s7e6aab68`、服务名为 `tradepass`。修改云托管环境或服务名称时，需要同步更新这两个值。文件上传下载仍经过业务后端完成租户权限、文件类型、长度和 SHA-256 校验，后端再通过开放接口服务管理私有对象存储。

## 开发说明

- 后端目前是单体应用，业务读写通过 MyBatis-Plus Mapper 操作数据库。
- 通用响应 DTO 在 `backend/src/main/java/com/tradepass/common/TradePassDtos.java`，接口请求/响应模型在 `backend/src/main/java/com/tradepass/dto/`。
- 小程序请求封装在 `miniprogram/utils/request.js`，会自动注入 token 和当前企业 ID。
- 开发接口仅在 `TRADEPASS_DEV_ENABLED=true` 时可用，生产环境会隐藏 `/api/dev/**`。
