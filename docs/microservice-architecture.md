# 贸易通微服务架构设计

## 统一命名

所有后端模块、Maven `artifactId`、Docker 服务名、Nacos 服务名统一使用 `tradepass-*` 前缀。

| 模块 | 职责 | 默认端口 |
| --- | --- | --- |
| `tradepass-common` | 公共响应、异常、DTO | 无 |
| `tradepass-gateway` | API 网关、统一入口、路由转发 | `1110` |
| `tradepass-auth` | 微信登录、手机号绑定、当前用户 | `1111` |
| `tradepass-company` | 企业信息、认证、实名、人脸、电子章、授权 | `1112` |
| `tradepass-trade` | 订单、客户/供应商交易数据 | `1113` |
| `tradepass-ranking` | 供方/需方首页、销售/采购排行 | `1114` |
| `tradepass-file` | 上传凭证、对象存储适配 | `1115` |

## 总体架构图

```mermaid
flowchart TD
  miniProgram["微信小程序"] --> gateway["tradepass-gateway"]

  gateway --> auth["tradepass-auth"]
  gateway --> company["tradepass-company"]
  gateway --> trade["tradepass-trade"]
  gateway --> ranking["tradepass-ranking"]
  gateway --> file["tradepass-file"]

  auth --> nacos["Nacos 注册发现"]
  company --> nacos
  trade --> nacos
  ranking --> nacos
  file --> nacos
  gateway --> nacos

  company --> mysql["MySQL 业务库"]
  trade --> mysql
  ranking --> mysql

  auth --> redis["Redis 缓存"]
  company --> redis
  ranking --> redis

  file --> objectStorage["对象存储 COS/OSS/MinIO"]
```

## 请求调用链

```mermaid
sequenceDiagram
  participant user as 用户
  participant mini as 微信小程序
  participant gw as tradepass-gateway
  participant nacos as Nacos
  participant auth as tradepass-auth
  participant company as tradepass-company
  participant ranking as tradepass-ranking

  user->>mini: 打开小程序
  mini->>gw: POST /api/auth/wechat-login
  gw->>nacos: 查询 tradepass-auth 实例
  gw->>auth: 转发登录请求
  auth-->>gw: token + 用户信息
  gw-->>mini: 登录结果

  mini->>gw: GET /api/me
  gw->>auth: 获取当前用户和企业摘要
  auth-->>mini: 当前用户/企业/角色

  mini->>gw: GET /api/home/supplier?period=month
  gw->>ranking: 获取供方首页排行
  ranking-->>mini: 企业名称 + 销售排行

  mini->>gw: POST /api/seals
  gw->>company: 提交电子章
  company-->>mini: 审核状态
```

## 服务边界图

```mermaid
flowchart LR
  subgraph entryLayer [入口层]
    gateway["tradepass-gateway"]
  end

  subgraph coreServices [核心业务服务]
    auth["tradepass-auth"]
    company["tradepass-company"]
    trade["tradepass-trade"]
    ranking["tradepass-ranking"]
    file["tradepass-file"]
  end

  subgraph infraLayer [基础设施]
    nacos["Nacos"]
    mysql["MySQL"]
    redis["Redis"]
    storage["对象存储"]
  end

  gateway --> auth
  gateway --> company
  gateway --> trade
  gateway --> ranking
  gateway --> file

  auth --> redis
  company --> mysql
  company --> redis
  trade --> mysql
  ranking --> mysql
  ranking --> redis
  file --> storage

  coreServices --> nacos
  gateway --> nacos
```

## Docker 部署拓扑

```mermaid
flowchart TD
  subgraph dockerNetwork [Docker Network: tradepass]
    gateway["tradepass-gateway:1110"]
    auth["tradepass-auth:1111"]
    company["tradepass-company:1112"]
    trade["tradepass-trade:1113"]
    ranking["tradepass-ranking:1114"]
    file["tradepass-file:1115"]
    nacos["tradepass-nacos:1116"]
    mysql["tradepass-mysql:1118"]
    redis["tradepass-redis:1119"]
  end

  browser["浏览器/微信开发者工具"] -->|"http://localhost:1110"| gateway
  gateway --> nacos
  gateway --> auth
  gateway --> company
  gateway --> trade
  gateway --> ranking
  gateway --> file
```

## 小程序页面展示图

```mermaid
flowchart TD
  appStart["打开小程序"] --> login["微信登录/绑定手机号"]
  login --> checkCompany["检查企业绑定状态"]
  checkCompany -->|未绑定| onboarding["企业入驻认证"]
  checkCompany -->|已绑定| home["首页"]

  home --> supplierHome["供方首页"]
  home --> buyerHome["需方首页"]
  home --> mePage["我的页面"]

  supplierHome --> salesRanking["客户销售业绩排名"]
  buyerHome --> purchaseRanking["采购业绩排名"]

  mePage --> companyInfo["上传公司信息"]
  mePage --> realName["实名认证"]
  mePage --> faceVerify["人脸录入"]
  mePage --> sealUpload["上传电子章"]
  mePage --> authorization["开授权"]
```

## 首页展示结构

```mermaid
flowchart TD
  homePage["首页"] --> companyCard["企业名称/当前身份"]
  homePage --> roleSwitch["身份切换: 供应商/采购商"]
  homePage --> periodTabs["统计筛选: 总/年/月"]
  homePage --> rankingList["业绩排行列表"]

  rankingList --> rankNo["排名"]
  rankingList --> counterparty["客户或供应商名称"]
  rankingList --> amount["交易金额"]
  rankingList --> orderCount["订单数"]
```

## 我的页面展示结构

```mermaid
flowchart TD
  mePage["我的"] --> profileCard["用户和企业信息"]
  mePage --> actionList["企业认证与授权入口"]

  actionList --> uploadCompany["上传公司信息"]
  actionList --> realName["实名认证"]
  actionList --> face["人脸录入"]
  actionList --> seal["上传电子章"]
  actionList --> authz["开授权"]
```

## 后续演进

- 当前阶段使用 Nacos 做服务注册发现，网关统一暴露 `/api/**`。
- 后续可以加入 Sentinel 做限流熔断，RocketMQ 做订单事件和排行异步统计。
- Seata 只建议在确实出现跨服务强一致事务后再引入，避免过早增加复杂度。
- MySQL 表结构已在 `backend/src/main/resources/schema.sql` 中准备，当前代码仍保留内存演示数据，下一步可替换为 MyBatis-Plus 持久化。
