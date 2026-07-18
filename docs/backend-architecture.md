# 后端架构现状

## 当前形态

后端是单个 Spring Boot 3 应用，运行端口为 `9999`。代码按 `controller`、`service`、`mapper`、`entity` 和 `dto` 分层，业务数据通过 MyBatis-Plus 持久化到 MySQL，数据库结构通过 Flyway 迁移维护。

当前没有 API 网关、Nacos、Redis、消息队列或拆分后的微服务。`docs/microservice-architecture.md` 仅记录未来业务规模扩大时的备选演进方案，不代表仓库现状。

## 主要模块

- `AuthController` / `AuthService`：微信登录、会话、手机号绑定、当前用户、企业切换。
- `CompanyController` / `CompanyService`：企业资料、邀请、成员授权、角色和认证状态。
- `TradeController` / `TradeService`：订单、合作方、合同模板、合同及签署审批。
- `RankingController` / `RankingService`：首页统计和客户/供应商排行。
- `FileController`：仅在开发环境提供上传凭证占位响应；生产环境需先接入对象存储。

## 数据与租户边界

- 登录会话使用随机 token，数据库仅保存摘要；注销会撤销服务端会话。
- 小程序在 `Authorization` 中传 token，在 `X-Company-Id` 中传当前企业。
- 受租户约束的查询由服务端读取并校验当前企业，不能依赖客户端默认企业 ID。
- 订单、合同、模板和成员列表接口采用分页响应：`items`、`total`、`page`、`size`、`hasMore`。

## 外部能力边界

微信登录和手机号能力依赖 `WECHAT_APP_SECRET`。实名、人脸、电子章、短信和对象存储尚未完成生产供应商接入；生产配置下不会模拟成功。开发配置可保留明确标识的本地联调流程。

## 基础设施

`docker-compose.yml` 当前只启动 MySQL。应用在宿主机通过 `java -jar` 或 IDEA 运行。
