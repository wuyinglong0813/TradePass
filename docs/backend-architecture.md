# 后端架构建议

## 结论

后端采用 Spring Cloud Alibaba 微服务架构，所有模块统一使用 `tradepass-*` 前缀。

## 当前推荐形态

当前服务拆分如下：

- `tradepass-gateway`：统一 API 入口和路由。
- `tradepass-auth`：微信登录、手机号绑定、当前用户。
- `tradepass-company`：企业信息、认证、实名、人脸、电子章、授权。
- `tradepass-trade`：订单和交易数据。
- `tradepass-ranking`：供方/需方首页、销售/采购排行。
- `tradepass-file`：上传凭证和对象存储适配。
- `tradepass-common`：公共响应、异常和 DTO。

部署上用 Docker Compose 管理：

- `tradepass-nacos`：服务注册发现。
- `tradepass-gateway`：网关。
- `tradepass-auth`、`tradepass-company`、`tradepass-trade`、`tradepass-ranking`、`tradepass-file`：业务服务。
- `mysql`：业务数据。
- `redis`：缓存、验证码、会话、排行热点缓存。

## Spring Cloud Alibaba 组件

- Nacos：服务注册发现，后续也可以承载配置中心。
- Gateway：统一入口、路由、鉴权前置、跨域处理。
- Sentinel：后续用于限流、熔断、降级。
- RocketMQ：后续用于订单事件、排行统计、审核通知。
- Seata：只有确实需要跨服务强一致事务时再引入。

## 当前实现边界

当前代码先完成微服务骨架、服务注册、网关路由、核心 API 和 Docker 编排。业务数据仍使用内存演示数据，MySQL 表结构已经准备好，下一步可以引入 MyBatis-Plus 并逐步持久化。

## 架构图

完整架构图、调用链、部署拓扑和小程序页面展示图见 `docs/microservice-architecture.md`。
