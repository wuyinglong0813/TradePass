# 财源通天 (TradePass)

面向企业贸易场景的微信小程序，Java 后端 + 原生微信小程序。

## 技术栈

- **后端**：Java 17 · Spring Boot 3.3.6 · JDBC · MySQL 8.4
- **小程序**：原生微信小程序
- **基础设施**：Docker Compose（MySQL + Redis）

## 项目结构

```
backend/        Spring Boot 单体应用（端口 9999）
miniprogram/    微信小程序
docs/           产品与开发文档
scripts/        启动/关闭脚本
```

## 快速开始

```bash
# 一键启动（MySQL + Redis + 应用）
./scripts/start.sh

# 关闭
./scripts/stop.sh
```

IDEA 开发：直接 Run `backend/.../TradepassApplication.java`

API 地址：`http://localhost:9999/api`

## 小程序

微信开发者工具打开 `miniprogram/` 目录。开发阶段 `urlCheck: false`，请求 `localhost:9999/api`。

页面：`pages/index/index`（首页）· `pages/me/me`（我的）· `pages/auth-manage/auth-manage`（授权管理）· `pages/role-manage/role-manage`（角色管理）· `pages/company-cert/company-cert`（企业认证）· `pages/order-detail/order-detail`（公司详情）

## 功能

- 微信登录 · 多公司 · 多角色 · 细粒度权限
- 邀请码加入企业 / 供方公司
- 首页排行（总/年/月）· 供方公司管理
- 授权审批 · 自定义角色 · 权限勾选
- 企业认证（公司信息 · 实名 · 电子章）

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:1118/tradepass?...` | 数据库连接 |
| `DB_USERNAME` | `tradepass` | 数据库用户 |
| `DB_PASSWORD` | `tradepass_pwd` | 数据库密码 |
| `WECHAT_APP_ID` | `wxd6d1e93a3868253e` | 小程序 AppID |
| `WECHAT_APP_SECRET` | （必填） | 小程序密钥 |
