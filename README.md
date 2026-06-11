# WenDao（闻道）- AI 新闻智能监控平台

基于 **若依（RuoYi-Vue）** 改造的全栈企业级 AI 新闻监控与智能分析平台。集成多源新闻抓取、关键词监控、AI 大模型解读、实时大屏推送等功能。

> 后端：Spring Boot 3.5 + MyBatis + Redis + WebSocket  
> 前端：Vue 3 + TypeScript + Element Plus + ECharts

---

## 🚀 核心功能

### 📡 AI 新闻模块（核心业务）

| 功能 | 说明 |
|------|------|
| **实时大屏** | WebSocket 实时推送新闻，ECharts 可视化（来源分布、情感分析、24h 趋势），在线客户端计数 |
| **新闻源管理** | 支持 RSS / API / 爬虫 / 搜索引擎四种抓取方式，主力+辅助双模式调度 |
| **文章管理** | 多条件筛选（标题/类型/语言/情感/推送状态），关联度评分，支持手动推送大屏 |
| **关键词监控** | 关键词驱动抓取，查询扩展（自动生成相关搜索词），关联度阈值过滤 |
| **AI 智能解读** | SSE 流式输出，Markdown 渲染 + Mermaid 流程图，多版本历史切换，导出 PDF/HTML |
| **提示词配置** | 支持 ANALYSIS / INTERPRET / AGGREGATE / COMPARE 四种类型，多对多关联新闻类型 |
| **模型管理** | 多厂商支持（DeepSeek / 硅基流动 / 阿里百炼 / 智谱 GLM / 火山引擎 / 自定义），API Key AES 加密存储，连接测试，按优先级+场景自动匹配，支持 OpenAI / Anthropic 双 API 格式 |

### 📰 新闻采集工作流程

```
定时任务 (每5分钟)
    │
    ├── ① 新闻源抓取 (fetchPrimarySources)
    │       └── 仅 PRIMARY 模式的新闻源（RSS/API/CRAWL）
    │            → 标题关键词匹配过滤
    │
    └── ② 关键词搜索 (fetchByKeywords)
            └── 所有激活的关键词
                 → AI 查询扩展（同义词生成）
                 → 多引擎搜索（Bing / 搜狗 / B站 / 微博）
                 → 原始关键词匹配过滤
                    │
                    ▼
            ③ 去重 & 入库 (news_article)
                    │
                    ▼
            ④ 异步 AI 分析 → 深度评分（摘要/标签/情感/类型/relevance）
                    │
                    ▼
            ⑤ 相关性阈值过滤（relevance ≥ 60 → status=0，< 60 → status=1 下架）
                    │
                    ▼
            ⑥ WebSocket 实时推送（仅 status=0）
                    │
                    ▼
            ⑦ 前端展示（文章列表 / 大屏看板 / 一键解读 SSE 流式）
```

**三层过滤机制：**

| 层级 | 位置 | 说明 |
|---|---|---|
| **第一层** | 入库前 | 标题必须包含激活关键词（中文子串匹配 / 英文短词独立单词边界匹配），不匹配直接丢弃 |
| **第二层** | AI 分析 | AI 模型综合评估文章相关性，输出 `relevance` 评分（0-100） |
| **第三层** | AI 分析后 | `relevance < 阈值`（默认 60）→ `status=1` 自动下架，前端不可见 |

**英文短词单词边界匹配：** 关键词长度 < 4 时（如 "AI"），使用单词边界检查，防止 "AI" 误匹配 "DETAIL"、"SHANGHAI" 等单词中的 "ai" 子串。

详细流程参见 [docs/news-workflow.md](docs/news-workflow.md)。

### 🏗️ 系统管理（若依标准功能）

| 模块 | 功能 |
|------|------|
| **用户管理** | 用户 CRUD、角色分配、部门归属、状态控制 |
| **角色管理** | 角色 CRUD、菜单权限分配、数据权限范围 |
| **菜单管理** | 菜单树维护、按钮权限、路由配置 |
| **部门管理** | 组织架构树 |
| **岗位管理** | 岗位字典维护 |
| **字典管理** | 字典类型+数据维护（如模型提供商、新闻类型等） |
| **参数配置** | 系统参数键值对管理 |
| **通知公告** | 系统通知发布与已读追踪 |

### 📊 系统监控

| 功能 | 说明 |
|------|------|
| **在线用户** | 当前登录用户列表，支持强退 |
| **定时任务** | Quartz 任务调度，支持 Cron 表达式，执行日志 |
| **数据监控** | Druid 连接池监控 |
| **服务监控** | 服务器 CPU/内存/磁盘/JVM 信息 |
| **缓存监控** | Redis 缓存键值查看与清理 |
| **操作日志** | 接口调用日志（@Log 注解记录） |
| **登录日志** | 登录历史与异常记录 |

### 🛠️ 开发工具

| 功能 | 说明 |
|------|------|
| **代码生成** | 数据库表 → Java/Vue/SQL 一键生成，支持 Velocity 模板自定义 |
| **表单构建** | 拖拽式表单设计器 |
| **API 文档** | SpringDoc / Swagger 在线文档 |

---

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.11 | 核心框架 |
| MyBatis | 3.0.5 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存 & Token 存储 |
| Druid | 1.2.28 | 数据库连接池 |
| Spring Security | 6.x | 认证 & 授权 |
| JWT | 0.9.1 | 无状态 Token 鉴权 |
| Quartz | 2.4.x | 定时任务调度 |
| WebSocket | - | 实时消息推送 |
| SpringDoc | 2.8.16 | API 文档 |
| FastJSON2 | 2.0.61 | JSON 序列化 |
| Apache POI | 4.1.2 | Excel 导入导出 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.26 | 渐进式框架 |
| TypeScript | 5.6.3 | 类型安全 |
| Vite | 6.4.1 | 构建工具 |
| Element Plus | 2.13.1 | UI 组件库 |
| Pinia | 3.0.4 | 状态管理 |
| Vue Router | 4.6.4 | 路由管理 |
| ECharts | 5.6.0 | 数据可视化 |
| Axios | 1.13.2 | HTTP 客户端 |
| markdown-it | 14.2.0 | Markdown 渲染 |
| Mermaid | 11.15.0 | 流程图渲染 |
| jsPDF | 4.2.1 | PDF 导出 |
| html2canvas | 1.4.1 | HTML 截图 |

---

## 📁 项目结构

```
WenDao/
├── console/                          # 后端 Maven 多模块项目
│   ├── wendao-admin/                 # 入口模块：启动类、Controller、application.yml
│   ├── wendao-framework/             # 核心框架：Security、JWT、AOP、WebSocket、全局异常
│   ├── wendao-system/                # 业务模块：Service、Mapper、Domain
│   ├── wendao-quartz/                # 定时任务模块
│   ├── wendao-generator/             # 代码生成器
│   ├── wendao-common/                # 公共模块：注解、工具类、BaseEntity
│   └── sql/                          # 数据库脚本
│       ├── wendao.sql                # 一键初始化脚本（合并版）
│
├── ui/                               # 前端 Vue3 项目
│   └── src/
│       ├── api/                      # API 请求模块
│       │   ├── news/                 # 新闻模块 API
│       │   └── system/               # 系统模块 API
│       ├── views/                    # 页面组件
│       │   ├── news/                 # AI 新闻页面
│       │   │   ├── dashboard/        # 实时大屏
│       │   │   ├── article/          # 文章管理 + AI 解读弹窗
│       │   │   ├── source/           # 新闻源管理
│       │   │   ├── keyword/          # 关键词监控
│       │   │   ├── typeConfig/       # 新闻类型配置
│       │   │   ├── promptConfig/     # 提示词配置
│       │   │   └── modelConfig/      # 模型管理
│       │   ├── system/               # 系统管理页面
│       │   ├── monitor/              # 监控页面
│       │   └── tool/                 # 开发工具页面
│       ├── components/               # 公共组件
│       ├── layout/                   # 布局组件
│       ├── router/                   # 路由配置
│       ├── store/                    # Pinia 状态管理
│       ├── types/                    # TypeScript 类型定义
│       └── utils/                    # 工具函数
│
└── CLAUDE.md                         # 开发指南
```

---

## ⚡ 快速开始

### 环境要求

- **JDK** 17+
- **Maven** 3.6+
- **MySQL** 8.0+
- **Redis** 6.0+
- **Node.js** 18+
- **Yarn** 1.22+

### 1. 初始化数据库

执行合并后的 SQL 脚本（一张表搞定）：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS \`ry-vue\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
mysql -u root -p ry-vue < console/sql/wendao.sql
```

### 2. 配置后端

编辑 `console/wendao-admin/src/main/resources/application-druid.yml`，修改数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://localhost:3306/ry-vue?useUnicode=true&characterEncoding=utf8
        username: root
        password: your_password
  redis:
    host: localhost
    port: 6379
    database: 3
```

### 3. 启动后端

```bash
cd console

# 方式一：Maven 直接启动
mvn spring-boot:run

# 方式二：打包后启动
mvn clean package -Dmaven.test.skip=true
java -jar wendao-admin/target/wendao-admin.jar
```

后端默认运行在 `http://localhost:8080`

### 4. 启动前端

```bash
cd ui

# 安装依赖
yarn --registry=https://registry.npmmirror.com

# 启动开发服务器
yarn dev
```

前端默认运行在 `http://localhost:80`，开发模式下 API 请求自动代理到 `localhost:8080`

### 5. 登录系统

打开浏览器访问 `http://localhost`，使用默认账号登录：

- **用户名：** `admin`
- **密码：** `admin123`

---

## ⚙️ 关键配置说明

### AES 加密密钥

`application.yml` 中的 `wendao.aes-key` 用于加密数据库中存储的 API Key 等敏感字段：

```yaml
wendao:
  aes-key: WenDao2026!AesKey#Secret@9876  # 必须是 32 字符
```

### AI 新闻模块配置

AI 模型配置已迁移至数据库 `news_model_config` 表，通过管理界面"新闻管理 → 模型管理"进行配置，支持 Redis 缓存（5分钟 TTL）。

```yaml
news:
  fetch-interval-ms: 300000       # 新闻抓取间隔（毫秒）
  query-expansion:
    enabled: true                  # 是否启用 AI 查询扩展
    max-terms: 3                   # 最大扩展词数
  ai:
    enabled: true                  # 是否启用 AI 分析
```

每个关键词可单独配置 `relevance_threshold`（默认 **60**，范围 0-100），低于阈值的文章自动下架。

### 文件上传

```yaml
wendao:
  profile: D:/wendao/uploadPath    # 上传文件存储路径
```

---

## 🔐 权限体系

- **认证方式：** 无状态 JWT Token（Header: `Authorization: Bearer <token>`，30 分钟过期）
- **权限模型：** RBAC（用户 → 角色 → 菜单/按钮权限）
- **数据权限：** 支持按部门/岗位等维度进行行级数据过滤（`@DataScope` 注解）
- **接口防护：** 支持限流（`@RateLimiter`）、防重复提交（`@RepeatSubmit`）、XSS 过滤

---

## 📡 WebSocket 实时推送

新闻大屏通过 WebSocket 实现实时推送：

- **连接地址：** `ws://localhost:8080/ws/news?token=<jwt_token>`
- **心跳机制：** 客户端发送 `ping`，服务端回复 `pong`
- **推送事件：** 新文章抓取完成后自动通过 `@EventListener` 广播 `NEW_ARTICLE` 消息
- **消息格式：**
  ```json
  {
    "type": "NEW_ARTICLE",
    "data": { /* NewsArticle 对象 */ },
    "timestamp": 1717939200000
  }
  ```

---

## 🧪 API 文档

启动后端后访问：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- Druid 监控：`http://localhost:8080/druid/`（账号：`wendao` / 密码：`123456`）

---

## 📝 开发指南

详见 [CLAUDE.md](./CLAUDE.md)，包含：

- 后端模块架构与分层说明
- 前端动态路由与权限控制原理
- 全栈开发工作流
- 自定义注解使用说明
- 代码生成器使用指南

---

## 📄 License

基于若依（RuoYi-Vue）框架二次开发，遵循 MIT 协议。
