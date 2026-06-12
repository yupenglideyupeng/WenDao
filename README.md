# WenDao（闻道 · 热点洞察）— 关键词驱动的 AI 热点资讯监控平台

基于 **若依（RuoYi-Vue）** 改造的全栈热点资讯监控与 AI 智能分析平台。以关键词为驱动，集成多源资讯抓取、AI 大模型解读、实时大屏推送等功能。

> 后端：Spring Boot 3.5 + MyBatis + Redis + WebSocket  
> 前端：Vue 3 + TypeScript + Element Plus + ECharts

---

## 🚀 核心功能

### 📡 AI 新闻模块

| 功能 | 说明 |
|------|------|
| **实时大屏** | WebSocket 实时推送新闻，ECharts 可视化（来源分布、情感分析、24h 趋势），在线客户端计数，国内外来源统计 |
| **新闻源管理** | 支持 RSS / API / 爬虫 / 搜索引擎四种抓取方式，主力(PRIMARY)+辅助(SUPPLEMENTARY)双模式调度 |
| **文章管理** | 多条件筛选（标题/类型/语言/情感/推送状态），关联度评分，一键 AI 解读 |
| **关键词监控** | 关键词驱动过滤，关联度阈值控制（低于阈值自动下架） |
| **AI 智能解读** | SSE 流式输出，Markdown 渲染 + Mermaid 流程图，导出 PDF/HTML |
| **提示词配置** | 支持 ANALYSIS / INTERPRET 等多种类型，多对多关联新闻类型，自定义 temperature/maxTokens |
| **模型管理** | 多厂商支持（DeepSeek / 硅基流动 / 阿里百炼 / 智谱 GLM / 火山引擎 / 自定义），API Key AES 加密存储，连接测试，按优先级+场景自动匹配，支持 OpenAI / Anthropic 双 API 格式 |
| **新闻类型配置** | 自定义新闻分类（如 AI科技、财经、医疗等），AI 自动分类 |

### 📰 新闻采集流程

```
定时任务 (每5分钟)
    │
    └── 抓取 PRIMARY 模式新闻源
         ├── RSS 源  → 解析 XML 提取标题/链接/摘要
         ├── API 源  → 调用 REST API (HackerNews/知乎/微博)
         ├── CRAWL 源 → Jsoup 爬取网页
         └── SEARCH 源 → 遍历关键词，AI扩展后调用搜索引擎(Bing/搜狗/B站/微博)
              │
              ▼
         ① 标题关键词匹配过滤（第一层）
              │
              ▼
         ② 批量 AI 相关性评分（第二层）
              │
              ▼
         ③ 阈值过滤 → 低于阈值不入库（第三层）
              │
              ▼
         ④ 入库 + 异步 AI 分析（摘要/标签/情感/分类）
              │
              ▼
         ⑤ WebSocket 实时推送
```

**三层过滤机制：**

| 层级 | 位置 | 说明 |
|---|---|---|
| **第一层** | 入库前 | 标题必须包含激活关键词（中文子串匹配 / 英文短词独立单词边界匹配），不匹配直接丢弃 |
| **第二层** | AI 评分 | 批量 AI 调用评估文章与 AI/科技领域相关性，输出 relevance 评分（0-100） |
| **第三层** | 评分后 | `relevance < 阈值`（默认 60）→ 不入库 |

详细流程参见 [docs/news-workflow.md](docs/news-workflow.md)。

---

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.x | 核心框架 |
| MyBatis | 3.0.x | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存 & Token 存储 |
| Druid | 1.2.x | 数据库连接池 |
| Spring Security | 6.x | 认证 & 授权 |
| JWT | 0.9.1 | 无状态 Token 鉴权 |
| Quartz | 2.4.x | 定时任务调度 |
| WebSocket | - | 实时消息推送 |
| SpringDoc | 2.8.x | API 文档 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.x | 渐进式框架 |
| TypeScript | 5.6.x | 类型安全 |
| Vite | 6.x | 构建工具 |
| Element Plus | 2.13.x | UI 组件库 |
| Pinia | 3.x | 状态管理 |
| ECharts | 5.6.x | 数据可视化 |
| markdown-it | 14.x | Markdown 渲染 |
| Mermaid | 11.x | 流程图渲染 |

---

## 📁 项目结构

```
WenDao/
├── console/                    # 后端 Maven 多模块项目
│   ├── wendao-admin/           # 入口模块：Controller、application.yml
│   ├── wendao-framework/       # 核心框架：Security、JWT、AOP、WebSocket
│   ├── wendao-system/          # 业务模块：Service、Mapper、Domain
│   ├── wendao-quartz/          # 定时任务模块
│   ├── wendao-generator/       # 代码生成器
│   ├── wendao-common/          # 公共模块：注解、工具类、BaseEntity
│   └── sql/                    # 数据库脚本（wendao.sql 一键初始化）
│
├── ui/                         # 前端 Vue3 项目
│   └── src/
│       ├── api/news/           # 新闻模块 API
│       ├── views/news/         # 新闻页面
│       │   ├── dashboard/      # 实时大屏
│       │   ├── article/        # 文章管理 + AI 解读
│       │   ├── source/         # 新闻源管理
│       │   ├── keyword/        # 关键词监控
│       │   ├── typeConfig/     # 新闻类型配置
│       │   ├── promptConfig/   # 提示词配置
│       │   └── modelConfig/    # 模型管理
│       └── ...
│
└── docs/                       # 文档
    └── news-workflow.md        # 新闻采集工作流程详解
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

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS wendao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
mysql -u root -p wendao < console/sql/wendao.sql
```

### 2. 配置后端

编辑 `console/wendao-admin/src/main/resources/application-druid.yml`，修改数据库和 Redis 连接：

```yaml
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://localhost:3306/wendao?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8
        username: root
        password: your_password
  data:
    redis:
      host: localhost
      port: 6379
      database: 4
```

### 3. 启动后端

```bash
cd console
mvn spring-boot:run
# 或打包后启动
mvn clean package -Dmaven.test.skip=true
java -jar wendao-admin/target/wendao-admin.jar
```

后端默认运行在 `http://localhost:8080`

### 4. 启动前端

```bash
cd ui
yarn --registry=https://registry.npmmirror.com
yarn dev
```

前端默认运行在 `http://localhost:80`，API 自动代理到 `localhost:8080`

### 5. 登录

- **用户名：** `admin`
- **密码：** `admin123`

---

## 📋 新闻模块配置指南（首次使用必读）

按以下顺序依次配置，后续可随时调整：

### 第一步：模型管理（news/modelConfig）

配置 AI 大模型连接信息，系统支持多个模型按优先级+场景自动匹配。

| 配置项 | 说明 | 示例 |
|--------|------|------|
| 配置名称 | 自定义标识 | "DeepSeek主模型" |
| 提供商 | 厂商 | DEEPSEEK / SILICONFLOW / BAILIAN / ZHIPU / VOLCENGINE / CUSTOM |
| API地址 | 接口地址 | `https://api.deepseek.com/v1/chat/completions` |
| API密钥 | 密钥（AES加密存储） | `sk-xxxx` |
| 模型名称 | 模型标识 | `deepseek-chat` |
| 优先级 | 数字越小越优先 | 1 |
| 适用场景 | 逗号分隔 | `INTERPRET,ANALYSIS` |
| API格式 | OpenAI/Anthropic | OPENAI |
| 支持流式 | SSE流式输出 | ✅ |
| 支持JSON模式 | 结构化输出 | ✅ |

> **INTERPRET** = 一键解读（需支持流式）  
> **ANALYSIS** = 文章分析（摘要/标签/情感/分类/相关性评分）  
> **ALL** = 所有场景

### 第二步：新闻类型配置（news/typeConfig）

定义新闻分类，AI 分析时会自动归类。

| 配置项 | 说明 | 示例 |
|--------|------|------|
| 类型编码 | 唯一标识 | `ai_tech` |
| 类型名称 | 显示名称 | AI科技 |
| 是否启用 | 启用后AI才会使用此分类 | ✅ |

### 第三步：提示词配置（news/promptConfig）

配置 AI 提示词模板，控制 AI 输出内容和风格。

| 配置项 | 说明 |
|--------|------|
| 提示词名称 | 自定义标识 |
| 类型 | INTERPRET（解读）/ ANALYSIS（分析） |
| 关联新闻类型 | 多对多，可为空（全局匹配） |
| System Prompt | 系统提示词，定义 AI 角色和输出要求 |
| Temperature | 创造性控制（0-1，越低越稳定） |
| Max Tokens | 最大输出长度 |

### 第四步：新闻源管理（news/source）

配置数据来源，系统定时抓取。

| 配置项 | 说明 | 示例 |
|--------|------|------|
| 来源名称 | 自定义标识 | "IT之家" |
| 来源类型 | 国内/国外 | 国内(type=0) |
| 来源地址 | RSS/API/网页 URL | `https://www.ithome.com/rss/` |
| 抓取方式 | RSS / API / CRAWL / SEARCH | RSS |
| 抓取模式 | PRIMARY=主力 / SUPPLEMENTARY=辅助 | PRIMARY |
| 优先级 | high / medium / low | high |
| 每次抓取数 | 每次最多入库篇数 | 15 |

> **只有 PRIMARY 模式的来源**会被定时任务抓取。SUPPLEMENTARY 来源作为备用，可手动触发。  
> **SEARCH 类型**的来源通过关键词遍历搜索，不走 RSS/API 抓取。

### 第五步：关键词监控（news/keyword）

配置监控关键词，用于标题匹配过滤。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| 关键词 | 监控的关键词文本 | 必填 |
| 分类 | 可选分类标签 | - |
| 是否启用 | 启用后参与过滤 | ✅ |
| 相关性阈值 | 低于此值的文章自动下架 | **60**（最低60） |

> 关键词仅负责**过滤**，不再独立驱动搜索。所有搜索由 SEARCH 类型的新闻源驱动。

---

## ⚙️ 关键配置说明

### AES 加密密钥

```yaml
wendao:
  aes-key: WenDao2026!AesKey#Secret@9876  # 必须是 32 字符，用于加密 API Key
```

### 文件上传路径

```yaml
wendao:
  profile: D:/wendao/uploadPath
```

### SSE 流式超时

```yaml
spring:
  mvc:
    async:
      request-timeout: 300000  # 5分钟，避免长解读被截断
```

---

## 🔐 权限体系

- **认证方式：** 无状态 JWT Token（Header: `Authorization: Bearer <token>`，30 分钟过期）
- **权限模型：** RBAC（用户 → 角色 → 菜单/按钮权限）
- **数据权限：** 支持按部门/岗位进行行级数据过滤（`@DataScope` 注解）
- **接口防护：** 限流（`@RateLimiter`）、防重复提交（`@RepeatSubmit`）、XSS 过滤

---

## 📡 WebSocket 实时推送

- **连接地址：** `ws://localhost:8080/ws/news?token=<jwt_token>`
- **心跳机制：** 客户端发送 `ping`，服务端回复 `pong`
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

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- Druid 监控：`http://localhost:8080/druid/`（账号：`wendao` / 密码：`123456`）

---

## 📝 开发指南

详见 [CLAUDE.md](./CLAUDE.md)，包含后端模块架构、前端动态路由、全栈开发工作流等。
