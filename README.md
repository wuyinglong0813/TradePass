# 贸易通

贸易通是一个面向企业贸易场景的微信小程序 MVP，包含 Java 后端和原生微信小程序端。当前版本围绕 Excel 原型中的供方页面、需方页面、我的页面落地，覆盖企业认证、供需角色首页、销售/采购排行、电子章和授权管理。

## 项目结构

```text
backend/       Spring Cloud Alibaba 微服务
miniprogram/   原生微信小程序
docs/          产品与开发路线文档
```

## 后端

技术栈：

- Java 17
- Spring Boot 3
- Spring Cloud 2023
- Spring Cloud Alibaba
- Spring Cloud Gateway
- Nacos
- Bean Validation
- Springdoc OpenAPI
- MySQL 表结构见 `backend/src/main/resources/schema.sql`

微服务模块统一使用 `tradepass-*` 前缀：

- `tradepass-common`：公共响应、异常、DTO。
- `tradepass-gateway`：统一入口，端口 `1110`。
- `tradepass-auth`：登录和当前用户，端口 `1111`。
- `tradepass-company`：企业认证、实名、人脸、电子章、授权，端口 `1112`。
- `tradepass-trade`：订单和交易数据，端口 `1113`。
- `tradepass-ranking`：首页和排行，端口 `1114`。
- `tradepass-file`：上传凭证，端口 `1115`。

本地启动：

```bash
cd backend
mvn -pl tradepass-gateway -am spring-boot:run
```

本地单独启动业务服务时，也可以替换 `-pl` 的模块名。完整微服务联调建议使用 Docker Compose。

Docker 启动：

```bash
./scripts/start.sh
```

也可以直接使用 Docker Compose：

```bash
docker compose up --build
```

Compose 会启动：

- `tradepass-gateway`：网关，端口 `1110`。
- `tradepass-auth`：认证服务，端口 `1111`。
- `tradepass-company`：企业服务，端口 `1112`。
- `tradepass-trade`：交易服务，端口 `1113`。
- `tradepass-ranking`：排行服务，端口 `1114`。
- `tradepass-file`：文件服务，端口 `1115`。
- `tradepass-nacos`：Nacos，宿主机端口 `1116`，gRPC 端口 `1117`。
- `mysql`：MySQL 8，宿主机端口 `1118`，首次启动会执行 `schema.sql`。
- `redis`：Redis 7，宿主机端口 `1119`。

## 小程序

用微信开发者工具打开 `miniprogram/` 目录即可。开发阶段已关闭 URL 校验，默认请求 `http://localhost:1110/api`。

当前页面：

- `pages/index/index`：供方/需方首页，支持身份切换、总/年/月排行筛选。
- `pages/me/me`：公司法人/管理员页面，包含公司信息、实名认证、人脸录入、电子章、授权入口。

## MVP 数据来源

首期销售/采购业绩排行采用“后台录入或导入订单数据”的路线。当前代码用内存种子订单模拟接口返回，后续可按 `schema.sql` 切换到 MySQL，并用定时任务生成 `trade_stat_daily` 与 `ranking_snapshot`。

## 架构建议

后端已经按 Spring Cloud Alibaba 微服务架构拆分，网关统一暴露 `http://localhost:1110/api/**`。

详细说明见：

- `docs/backend-architecture.md`
- `docs/microservice-architecture.md`
