# CLAUDE.md

This file provides guidance to Claude Code when working in this repository.

## Project

贸易通 (TradePass) — enterprise trade WeChat mini-program. **Single Spring Boot app** + native mini-program frontend.

## Build & Run

```bash
# Start everything (MySQL + Redis + app on :9999)
./scripts/start.sh

# Stop
./scripts/stop.sh

# Build only
cd backend && mvn -DskipTests package

# IDEA: run TradepassApplication.java directly
```

API: `http://localhost:9999/api`

## Backend

**Single module** under `backend/`. Java 17, Spring Boot 3.3.6, JDBC (JdbcTemplate), MySQL.

```
backend/src/main/java/com/tradepass/
  TradepassApplication.java     # @SpringBootApplication
  common/                       # ApiResponse, BusinessException, TradePassDtos, GlobalExceptionHandler
  controller/
    AuthController.java         # Login, /me, dev user switch, DB init (@PostConstruct)
    CompanyController.java      # Company CRUD, authorizations, roles, invites, join
    TradeController.java        # Orders, counterparties
    RankingController.java      # Home page, rankings
    FileController.java         # Upload tokens (mock)
```

- All config via env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `WECHAT_APP_ID`, `WECHAT_APP_SECRET`
- Tables auto-created in `AuthController.initData()`
- `ApiResponse<T>` — `code=0` success, wraps all responses
- DTOs as Java records in `TradePassDtos.java`

## Miniprogram

8 pages, 2-tab bar (首页/我的). `utils/request.js` wraps `wx.request`, auto-injects token, unwraps `ApiResponse`.

## Docker

`docker-compose.yml` — MySQL + Redis only. App runs on host via `java -jar`.
`backend/Dockerfile` — single JAR, port 9999, for cloud deployment.
