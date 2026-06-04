# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a customized **WenDao-Vue** rapid development platform — a Spring Boot + Vue3 full-stack web application for enterprise management systems. It is split into two independent subprojects:

- **`console/`** — Java Spring Boot backend (WenDao-Vue v3.9.2, Spring Boot 3.5.x, JDK 17)
- **`ui/`** — Vue 3 + TypeScript + Vite frontend (WenDao-Vue3-TypeScript)

## Backend (`console/`)

### Build & Run

```bash
# From console/ directory:
cd console

# Package (skip tests):
mvn clean package -Dmaven.test.skip=true

# Run the JAR:
java -jar wendao-admin/target/wendao-admin.jar

# Or use Maven directly:
mvn spring-boot:run
```

Windows batch scripts are also available under `console/bin/`: `run.bat`, `package.bat`, `clean.bat`.

### Backend Architecture (Maven Modules)

| Module | Purpose |
|---|---|
| `wendao-admin` | Entry point (`WenDaoApplication.java`), HTTP controllers, `application.yml` |
| `wendao-framework` | Core infrastructure: Spring Security config, JWT filter, AOP aspects (logging, rate limiting, data scope, dynamic datasource), global exception handler, server monitoring |
| `wendao-system` | Business services, domain entities, MyBatis mappers for system features (user, role, menu, dept, config, dict, notice, login log, operation log) |
| `wendao-quartz` | Scheduled task (cron job) management with Quartz |
| `wendao-generator` | Code generator — generates CRUD Java/Vue/SQL from database tables using Apache Velocity templates |
| `wendao-common` | Shared annotations (`@Log`, `@DataScope`, `@RateLimiter`, `@RepeatSubmit`, `@Anonymous`), base classes (`BaseController`, `BaseEntity`, `TreeEntity`), enums, utilities, XSS/referer filters |

**Layering within each business module:** `controller` → `service`/`service.impl` → `mapper` (MyBatis XML in `resources/mapper/`)

### Key Backend Config

- **Database:** MySQL, database name `ry-vue`, configured in `application-druid.yml` (master/slave with Druid connection pool)
- **Redis:** Required for caching and JWT token storage (`localhost:6379`, database 0, no password by default)
- **Server port:** `8080`
- **Auth:** Stateless JWT (token header: `Authorization: Bearer <token>`, 30-minute expiry). Passwords hashed with BCrypt.
- **API docs:** SpringDoc/OpenAPI at `/swagger-ui.html` and `/v3/api-docs`
- **Druid console:** `/druid/*` (login: `wendao`/`123456`)
- **File upload path:** `D:/wendao/uploadPath` (configurable via `wendao.profile`)
- **SQL init scripts:** `console/sql/ry_20260417.sql` (main schema), `console/sql/quartz.sql` (Quartz tables)

### Key Custom Annotations (from `wendao-common`)

- `@Anonymous` — marks controller methods as publicly accessible (bypasses Spring Security)
- `@DataScope` — applies row-level data permission filtering on MyBatis queries
- `@Log` — records operation logs
- `@RateLimiter` — rate limits endpoints
- `@RepeatSubmit` — prevents duplicate form submission
- `@DataSource` — switches between master/slave datasources
- `@Excel` / `@Excels` — maps entity fields to Excel columns for import/export

## Frontend (`ui/`)

### Build & Run

```bash
cd ui

# Install dependencies:
yarn --registry=https://registry.npmmirror.com

# Dev server (port 80, proxies /dev-api → localhost:8080):
yarn dev

# Build for staging:
yarn build:stage

# Build for production:
yarn build:prod
```

The dev server opens at `http://localhost:80`. The Vite proxy rewrites `/dev-api` requests to `http://localhost:8080` (backend).

### Frontend Architecture

```
ui/src/
├── api/              # API request modules (one file per feature area)
├── components/       # Shared Vue components
├── layout/           # App layout (sidebar, navbar, etc.)
├── router/           # Vue Router config (constantRoutes + dynamicRoutes)
├── store/modules/    # Pinia stores (user, permission, settings, tagsView, app, dict, lock)
├── utils/            # Utilities (request.ts = Axios instance, auth.ts, validate.ts, etc.)
├── views/            # Page components (organized by feature)
└── plugins/          # Plugins (auth.ts for permission directives, cache.ts)
```

**Tech stack:** Vue 3.5, TypeScript 5.6, Vite 6, Element Plus 2.13, Pinia 3.0, Vue Router 4.6, Axios, ECharts 5.6

### Key Frontend Patterns

- **Dynamic routing:** On login, the frontend calls `getRouters()` to fetch the user's menu tree from the backend. `store/modules/permission.ts` converts backend component strings to actual Vue component imports via `import.meta.glob('./../../views/**/*.vue')`.
- **Auth model:** JWT token stored via `utils/auth.ts` (uses `js-cookie`). Axios interceptor in `utils/request.ts` attaches `Authorization: Bearer <token>` to every request. On 401 response, user is prompted to re-login.
- **Permission directives:** `plugins/auth.ts` provides `v-hasPermi` and `v-hasRole` directives for conditional rendering.
- **State management:** Pinia stores — `user` (token, user info, roles, permissions), `permission` (routes), `settings` (theme, layout options), `tagsView` (open page tabs).
- **API proxy config** in `vite.config.ts`: `/dev-api` → `http://localhost:8080`. Production builds need Nginx reverse proxy or similar.
- **Environment files:** `.env.development` (dev), `.env.production`, `.env.staging`. Key var: `VITE_APP_BASE_API` (base URL prefix for API calls).

## Full-Stack Development Workflow

1. Start MySQL and Redis locally
2. Initialize the database: run `console/sql/ry_20260417.sql` and `console/sql/quartz.sql`
3. Start the backend: `cd console && mvn spring-boot:run` (or run the JAR)
4. Start the frontend: `cd ui && yarn dev`
5. Open `http://localhost:80`, login with `admin`/`admin123`

## Adding a New Feature (Full-Stack)

1. Create the database table
2. Use the code generator (`wendao-generator`) via the admin UI at "系统工具 → 代码生成" to scaffold the CRUD code, OR manually:
   - Backend: Entity in `wendao-common` domain package → Mapper interface + XML in `wendao-system` → Service + ServiceImpl → Controller in `wendao-admin`
   - Frontend: API module in `ui/src/api/` → View pages in `ui/src/views/` → Add route to the menu via admin UI
3. If the controller method needs to bypass auth, annotate with `@Anonymous`
