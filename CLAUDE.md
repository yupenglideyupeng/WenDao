# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目概述

这是一个基于若依（RuoYi-Vue）改造的 **WenDao-Vue（闻道 · 热点洞察）** 快速开发平台——Spring Boot + Vue3 全栈企业级管理系统，并扩展了关键词驱动的 AI 热点资讯监控与解读功能。项目分为两个独立的子项目：

- **`console/`** — Java Spring Boot 后端（WenDao-Vue v3.9.2，Spring Boot 3.5.x，JDK 17）
- **`ui/`** — Vue 3 + TypeScript + Vite 前端（WenDao-Vue3-TypeScript）

## 后端（`console/`）

### 构建与运行

```bash
# 在 console/ 目录下执行：
cd console

# 打包（跳过测试）：
mvn clean package -Dmaven.test.skip=true

# 运行 JAR 包：
java -jar wendao-admin/target/wendao-admin.jar

# 或直接使用 Maven 运行：
mvn spring-boot:run
```

Windows 批处理脚本位于 `console/bin/` 目录下：`run.bat`、`package.bat`、`clean.bat`。

### 后端架构（Maven 模块）

| 模块 | 用途 |
|---|---|
| `wendao-admin` | 入口模块（`WenDaoApplication.java`），HTTP 控制器，`application.yml` |
| `wendao-framework` | 核心基础设施：Spring Security 配置、JWT 过滤器、AOP 切面（日志、限流、数据权限、动态数据源）、全局异常处理、WebSocket、服务监控 |
| `wendao-system` | 业务服务层，领域实体，MyBatis Mapper（用户、角色、菜单、部门、配置、字典、公告、登录日志、操作日志、新闻相关业务） |
| `wendao-quartz` | 定时任务（Cron Job）管理，基于 Quartz |
| `wendao-generator` | 代码生成器——根据数据库表使用 Apache Velocity 模板生成 CRUD 的 Java/Vue/SQL 代码 |
| `wendao-common` | 公共模块：自定义注解（`@Log`、`@DataScope`、`@RateLimiter`、`@RepeatSubmit`、`@Anonymous`）、基类（`BaseController`、`BaseEntity`、`TreeEntity`）、枚举、工具类、XSS/Referer 过滤器 |

**业务模块分层：** `controller` → `service`/`service.impl` → `mapper`（MyBatis XML 位于 `resources/mapper/`）

### 后端关键配置

- **数据库：** MySQL，数据库名 `ry-vue`，配置在 `application-druid.yml` 中（主从模式，Druid 连接池）
- **Redis：** 用于缓存和 JWT Token 存储（`localhost:6379`，database 3，默认无密码）
- **服务端口：** `8080`
- **认证方式：** 无状态 JWT（Token 请求头：`Authorization: Bearer <token>`，30 分钟过期）。密码使用 BCrypt 加密。
- **API 文档：** SpringDoc/OpenAPI，访问 `/swagger-ui.html` 和 `/v3/api-docs`
- **Druid 控制台：** `/druid/*`（账号：`wendao` / 密码：`123456`）
- **文件上传路径：** `D:/wendao/uploadPath`（可通过 `wendao.profile` 配置）
- **AES 加密密钥：** `wendao.aes-key`（32 字符，用于 API Key 等敏感字段加密）
- **SQL 初始化脚本：** `console/sql/wendao.sql`（一键初始化所有表结构+数据）

### 关键自定义注解（来自 `wendao-common`）

- `@Anonymous` — 标记控制器方法为公开访问（绕过 Spring Security 认证）
- `@DataScope` — 在 MyBatis 查询上应用行级数据权限过滤
- `@Log` — 记录操作日志
- `@RateLimiter` — 接口限流
- `@RepeatSubmit` — 防止表单重复提交
- `@DataSource` — 切换主从数据源
- `@Excel` / `@Excels` — 将实体字段映射到 Excel 列，用于导入导出

## 前端（`ui/`）

### 构建与运行

```bash
cd ui

# 安装依赖：
yarn --registry=https://registry.npmmirror.com

# 开发服务器（端口 80，代理 /dev-api → localhost:8080）：
yarn dev

# 构建测试环境：
yarn build:stage

# 构建生产环境：
yarn build:prod
```

开发服务器运行在 `http://localhost:80`。Vite 代理将 `/dev-api` 请求转发到 `http://localhost:8080`（后端）。

### 前端架构

```
ui/src/
├── api/              # API 请求模块（按功能区域划分，每个功能一个文件）
├── components/       # 公共 Vue 组件
├── layout/           # 应用布局（侧边栏、导航栏等）
├── router/           # Vue Router 配置（constantRoutes + dynamicRoutes）
├── store/modules/    # Pinia 状态管理（user, permission, settings, tagsView, app, dict, lock）
├── utils/            # 工具函数（request.ts = Axios 实例, auth.ts, validate.ts 等）
├── views/            # 页面组件（按功能模块组织）
│   ├── news/         # AI 新闻模块
│   │   ├── dashboard/    # 实时大屏
│   │   ├── article/      # 文章管理 + AI 解读弹窗
│   │   ├── source/       # 新闻源管理
│   │   ├── keyword/      # 关键词监控
│   │   ├── typeConfig/   # 新闻类型配置
│   │   ├── promptConfig/ # 提示词配置
│   │   └── modelConfig/  # 模型管理
│   ├── system/       # 系统管理页面
│   ├── monitor/      # 监控页面
│   └── tool/         # 开发工具页面
└── plugins/          # 插件（auth.ts 权限指令, cache.ts 缓存）
```

**技术栈：** Vue 3.5、TypeScript 5.6、Vite 6、Element Plus 2.13、Pinia 3.0、Vue Router 4.6、Axios、ECharts 5.6

### 前端关键机制

- **动态路由：** 登录后，前端调用 `getRouters()` 从后端获取用户菜单树。`store/modules/permission.ts` 通过 `import.meta.glob('./../../views/**/*.vue')` 将后端返回的组件字符串转换为实际的 Vue 组件导入。
- **认证模型：** JWT Token 通过 `utils/auth.ts` 存储（使用 `js-cookie`）。`utils/request.ts` 中的 Axios 拦截器为每个请求添加 `Authorization: Bearer <token>` 请求头。收到 401 响应时，提示用户重新登录。
- **权限指令：** `plugins/auth.ts` 提供 `v-hasPermi` 和 `v-hasRole` 指令，用于条件渲染。
- **状态管理：** Pinia Store——`user`（Token、用户信息、角色、权限）、`permission`（路由）、`settings`（主题、布局选项）、`tagsView`（已打开的页面标签）、`dict`（字典缓存）。
- **API 代理配置** 在 `vite.config.ts` 中：`/dev-api` → `http://localhost:8080`。生产构建需要 Nginx 反向代理或类似方案。
- **环境文件：** `.env.development`（开发）、`.env.production`（生产）、`.env.staging`（测试）。关键变量：`VITE_APP_BASE_API`（API 调用的基础 URL 前缀）。

## AI 新闻模块架构

### 后端控制器（`console/wendao-admin/.../controller/news/`）

| 控制器 | 路径前缀 | 说明 |
|---|---|---|
| `NewsArticleController` | `/news/article` | 文章 CRUD，支持 WebSocket 推送 |
| `NewsSourceController` | `/news/source` | 新闻源 CRUD（RSS/API/CRAWL/SEARCH），支持 fetch_mode |
| `NewsKeywordController` | `/news/keyword` | 关键词 CRUD，支持 relevance_threshold、expand_queries |
| `NewsTypeConfigController` | `/news/typeConfig` | 新闻类型分类 CRUD |
| `NewsPromptConfigController` | `/news/promptConfig` | AI 提示词模板 CRUD，多对多关联新闻类型 |
| `NewsModelConfigController` | `/news/model` | AI 模型配置 CRUD + 连接测试 |
| `NewsDashboardController` | `/news/dashboard` | 大屏统计、最新文章、在线客户端数、Feed 分页 |
| `NewsInterpretController` | `/news/article` | SSE 流式 AI 解读，支持历史版本查询 |

### 前端 API 模块（`ui/src/api/news/`）

| 文件 | 接口 |
|---|---|
| `article.ts` | `/news/article` CRUD |
| `source.ts` | `/news/source` CRUD |
| `keyword.ts` | `/news/keyword` CRUD |
| `typeConfig.ts` | `/news/typeConfig` CRUD |
| `promptConfig.ts` | `/news/promptConfig` CRUD |
| `modelConfig.ts` | `/news/model` CRUD + 测试连接 |
| `dashboard.ts` | 大屏统计/最新文章/在线数/Feed |
| `interpretation.ts` | SSE 流式解读 + 历史记录查询 |

### WebSocket 实时推送（`wendao-framework/.../websocket/`）

| 文件 | 说明 |
|---|---|
| `NewsWebSocketHandler.java` | WebSocket 处理器，管理并发客户端（ConcurrentHashMap），支持 ping/pong 心跳，广播 `NEW_ARTICLE` 消息，监听 `NewsFetchedEvent` 事件自动推送 |
| `NewsWebSocketInterceptor.java` | 握手拦截器，从查询参数中提取 JWT Token 进行认证 |

## 全栈开发流程

1. 本地启动 MySQL 和 Redis
2. 初始化数据库：`mysql -u root -p ry-vue < console/sql/wendao.sql`
3. 启动后端：`cd console && mvn spring-boot:run`（或运行 JAR 包）
4. 启动前端：`cd ui && yarn dev`
5. 打开 `http://localhost:80`，使用 `admin` / `admin123` 登录

## 新增功能开发流程（全栈）

1. 创建数据库表
2. 通过管理界面"系统工具 → 代码生成"使用代码生成器（`wendao-generator`）生成 CRUD 脚手架代码，或手动编写：
   - 后端：`wendao-system` 中的 Entity → Mapper 接口 + XML → Service + ServiceImpl → `wendao-admin` 中的 Controller
   - 前端：`ui/src/api/` 中的 API 模块 → `ui/src/views/` 中的页面组件 → 通过管理界面添加菜单路由
3. 如果控制器方法需要绕过认证，添加 `@Anonymous` 注解
4. 如果菜单需要前端动态路由注册，在 `ui/src/router/index.ts` 的 `dynamicRoutes` 中添加路由配置并设置 `permissions` 字段
