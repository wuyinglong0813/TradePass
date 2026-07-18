# CLAUDE.md

This file provides guidance to Claude Code when working in this repository.

## Project

财源通天 (TradePass) — enterprise trade WeChat mini-program. **Single Spring Boot app** + native mini-program frontend.

## Build & Run

```bash
# Start everything (MySQL + app on :9999)
./scripts/start.sh

# Stop
./scripts/stop.sh

# Build only
cd backend && mvn -DskipTests package

# IDEA: run TradepassApplication.java directly
```

API: `http://localhost:9999/api`

## Backend

**Single module** under `backend/`. Java 17, Spring Boot 3.3.6, MyBatis-Plus, Flyway, MySQL.

```
backend/src/main/java/com/tradepass/
  TradepassApplication.java     # @SpringBootApplication
  common/                       # ApiResponse, BusinessException, TradePassDtos, GlobalExceptionHandler
  controller/
    AuthController.java         # Login, /me, dev user switch, DB init (@PostConstruct)
    CompanyController.java      # Company CRUD, authorizations, roles, invites, join
    TradeController.java        # Orders, counterparties
    RankingController.java      # Home page, rankings
    FileController.java         # Dev-only upload credential placeholder
```

- Main config via env vars: `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `WECHAT_APP_ID`, `WECHAT_APP_SECRET`
- Schema changes are versioned under `backend/src/main/resources/db/migration/`
- `ApiResponse<T>` — `code=0` success, wraps all responses
- DTOs as Java records in `TradePassDtos.java`

## Miniprogram

18 pages, 3-tab bar (首页/企业/我的). `utils/request.js` wraps `wx.request`, auto-injects token and current company ID, and unwraps `ApiResponse`.

## Docker

`docker-compose.yml` — MySQL only. App runs on host via `java -jar`.
`backend/Dockerfile` — single JAR, port 9999, for cloud deployment.
