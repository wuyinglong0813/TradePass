# 财源通天 (TradePass)

面向企业贸易、合同签署与企业协作场景的微信小程序。项目采用 Java 后端 + 原生微信小程序前端，当前以 MVP 单体应用方式快速迭代。

## 技术栈

- **后端**：Java 17 · Spring Boot 3.3.6 · Spring Web · MyBatis-Plus · MySQL 8.4
- **小程序**：原生微信小程序 · WXML/WXSS/JS
- **基础设施**：Docker Compose（MySQL）

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
# 一键启动（MySQL + 应用）
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

当前 MVP 阶段由后端启动时自动创建表并写入演示数据，逻辑位于 `DatabaseInitializer`。数据库默认由 Docker Compose 提供：

- MySQL：`localhost:1118`
- database：`tradepass`
- username：`tradepass`
- password：`tradepass_pwd`

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` | `localhost` | 数据库主机 |
| `DB_PORT` | `1118` | 数据库端口 |
| `DB_USERNAME` | `tradepass` | 数据库用户 |
| `DB_PASSWORD` | `tradepass_pwd` | 数据库密码 |
| `WECHAT_APP_ID` | `wxd6d1e93a3868253e` | 小程序 AppID |
| `WECHAT_APP_SECRET` | 空 | 小程序密钥 |
| `TRADEPASS_DEV_ENABLED` | `false` | 是否启用 `/api/dev/**` 和开发占位能力；`dev` profile 会开启 |
| `TRADEPASS_VERIFICATION_AUTO_APPROVE` | `false` | 是否允许开发环境模拟认证结果，生产环境应保持关闭 |

## 开发说明

- 后端目前是单体应用，业务读写通过 MyBatis-Plus Mapper 操作数据库。
- 通用响应 DTO 在 `backend/src/main/java/com/tradepass/common/TradePassDtos.java`，接口请求/响应模型在 `backend/src/main/java/com/tradepass/dto/`。
- 小程序请求封装在 `miniprogram/utils/request.js`，会自动注入 token 和当前企业 ID。
- 开发接口仅在 `TRADEPASS_DEV_ENABLED=true` 时可用，生产环境会隐藏 `/api/dev/**`。
