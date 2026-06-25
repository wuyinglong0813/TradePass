# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

贸易通 (TradePass) — enterprise trade WeChat mini-program MVP. Java microservices backend + native WeChat mini-program frontend. Currently uses in-memory demo data; MySQL schema is prepared but not yet wired in.

## Build & Run

```bash
# Build all modules (skip tests)
cd backend && mvn -DskipTests package

# Run a single service locally (replace module name as needed)
cd backend && mvn -pl tradepass-gateway -am spring-boot:run

# Full Docker Compose (builds JARs, starts all services + infra)
./scripts/start.sh
# or directly:
cd backend && mvn -q -DskipTests package && docker compose up --build -d

# Stop everything
docker compose down
```

**Service ports** (all on localhost when Docker is up):

| Service | Port |
|---|---|
| Gateway | 1110 |
| Auth | 1111 |
| Company | 1112 |
| Trade | 1113 |
| Ranking | 1114 |
| File | 1115 |
| Nacos console | 1116 |
| MySQL | 1118 |
| Redis | 1119 |

API base URL: `http://localhost:1110/api`

## Backend Architecture

**Tech stack**: Java 17, Spring Boot 3.3.6, Spring Cloud 2023.0.4, Spring Cloud Alibaba 2023.0.3.3, Maven multi-module.

**Module layout** (all under `backend/`, parent POM `tradepass-services`):

- `tradepass-common` — shared `ApiResponse<T>` (record, code=0 means success), `BusinessException`, `GlobalExceptionHandler`, `TradePassDtos` (all DTO records in one file)
- `tradepass-gateway` — Spring Cloud Gateway, routes `/api/**` paths to downstream `lb://` services via Nacos discovery. Routes defined in `application.yml`.
- `tradepass-auth` — WeChat login, phone binding, `/api/me` endpoint. Demo user/company kept in mutable fields.
- `tradepass-company` — Company info, certification, seals, authorizations.
- `tradepass-trade` — Orders, trade data.
- `tradepass-ranking` — Home pages (`/api/home/supplier`, `/api/home/buyer`), rankings. In-memory seed orders with period filtering (total/year/month).
- `tradepass-file` — File upload tokens, object storage adapter.

**Key patterns**:
- Every controller returns `ApiResponse<T>` — use `ApiResponse.ok(data)` for success.
- All DTOs are Java records nested inside `TradePassDtos` in the common module. Add new DTOs there.
- Request validation uses `@Valid` + `@NotBlank` on record components defined as inner records in controllers.
- Each service module has its own `application.yml` with `server.port`, `spring.application.name`, and Nacos discovery config. The `NACOS_SERVER_ADDR` env var defaults to `localhost:1116`.
- `scanBasePackages = "com.tradepass"` on business service application classes so they pick up `GlobalExceptionHandler` from common.
- Gateway is the **only** module without `scanBasePackages` — it must not accidentally load MVC config.

## Docker

Single Dockerfile at `backend/Dockerfile` — multi-module aware via `SERVICE_MODULE` build arg. Copies the specific module's JAR.

Docker Compose (`docker-compose.yml`) starts: Nacos, MySQL 8.4, Redis 7.4, and all 6 services. MySQL auto-initializes from `backend/src/main/resources/schema.sql` on first start. All services communicate over the `tradepass` bridge network using container names.

## Miniprogram

Native WeChat mini-program (not a framework). Open `miniprogram/` in WeChat Developer Tools.

- **3 pages**: `pages/index/index` (home with role toggle), `pages/me/me` (profile, certifications), `pages/company-bind/company-bind` (company binding)
- **`utils/request.js`**: Promise wrapper around `wx.request`, auto-injects `Authorization` header from globalData/storage, unwraps `ApiResponse` — resolves with `data.data` on `code === 0`, rejects otherwise.
- **`app.js`**: `onLaunch` calls `wx.login` then `POST /api/auth/wechat-login`, stores token.
- Dev mode has URL check disabled (`urlCheck: false` in `project.config.json`), targeting `localhost:1110/api`.

## MySQL Schema

Tables in `backend/src/main/resources/schema.sql`: `sys_user`, `company`, `company_member`, `company_certification`, `real_name_verification`, `company_seal`, `company_authorization`, `counterparty_relation`, `trade_order`, `trade_order_item`, `trade_stat_daily`, `ranking_snapshot`. Currently not connected — controllers use in-memory data. Next step is to introduce MyBatis-Plus and persist.

## Docs

- `docs/microservice-architecture.md` — detailed architecture diagrams (Mermaid), call flows, service boundaries, deployment topology
- `docs/backend-architecture.md` — architecture decisions and evolution plan
- `docs/mvp-roadmap.md` — 6-phase development roadmap
