# 新闻采集 → 展示 完整工作流程

## 架构概览

```
定时任务 (每5分钟)
    │
    ├── ① 新闻源抓取 (fetchPrimarySources)
    │       └── 仅 PRIMARY 模式的新闻源
    │            → 关键词匹配过滤（三层过滤第一层）
    │
    └── ② 关键词搜索 (fetchByKeywords)
            └── 所有激活的关键词
                 → AI 查询扩展 → 多引擎搜索
                 → 原始关键词匹配过滤（三层过滤第一层）
                    │
                    ▼
            ③ 去重 & 入库 (news_article)
                    │
                    ▼
            ④ 异步 AI 分析 → 相关性评分（三层过滤第二层）
                    │
                    ▼
            ⑤ 阈值过滤 → status=1 下架（三层过滤第三层）
                    │
                    ▼
            ⑥ WebSocket 实时推送（仅 status=0）
                    │
                    ▼
            ⑦ 前端展示
```

---

## 三层过滤机制

为保证入库新闻与关键词高度相关，系统在三个环节依次过滤：

```
第一层：标题关键词匹配（入库前，代码层面）
    │
    ├── 新闻源抓取：标题必须包含至少一个激活关键词
    │     ├── 中文源(type=0) → 匹配中文关键词（子串）
    │     ├── 英文源(type=1) → 匹配英文关键词（子串/单词边界）
    │     └── 不匹配 → 直接丢弃，不入库
    │
    ├── 关键词搜索：标题必须包含原始关键词（非扩展词）
    │     ├── 中文关键词 → 子串匹配
    │     ├── 英文短词(<4字符) → 独立单词边界匹配（防止 "AI" 匹配 "DETAIL"）
    │     └── 不匹配 → 直接丢弃，不入库
    │
    └── 微博热搜：英文短词做单词边界检查（防止 "AI" 匹配 "SHANGHAI"）
    │
    ▼
第二层：AI 深度分析评分
    └── relevance: 0-100（AI 模型综合评估）
    │
    ▼
第三层：相关性阈值过滤（默认 60）
    ├── relevance >= 60 → status=0（正常展示）
    └── relevance < 60  → status=1（自动下架，前端不可见）
```

### 英文短词单词边界匹配

关键词长度 < 4 个字符时（如 "AI"、"GPT"），使用单词边界匹配，防止子串误匹配：

| 标题 | 关键词 "AI" | 匹配结果 |
|---|---|---|
| "New AI breakthrough announced" | ✅ 匹配 | AI 是独立单词 |
| "DETAIL report released" | ❌ 不匹配 | "DETAIL" 中的 "AI" 不是独立单词 |
| "SHANGHAI news today" | ❌ 不匹配 | "SHANGHAI" 中的 "AI" 不是独立单词 |

---

## ① 新闻源抓取（SOURCE）

**触发条件：** `news_source` 表中 `fetch_mode = 'PRIMARY'` 且 `status = '0'`（启用）的记录

**数据来源：** 根据 `fetch_type` 走不同抓取方式：

| fetch_type | 说明 | 实现 |
|---|---|---|
| RSS | 解析 RSS/Atom XML | `fetchRss()` — 提取 `<item>/<entry>` 中的 title、link、description、pubDate |
| API | 调用 REST API 获取 JSON | `fetchApi()` — 支持 HackerNews、知乎热榜、微博热搜、通用 JSON |
| CRAWL | Jsoup 爬取 HTML | `fetchCrawl()` — 提取页面中 `<a>` 标签的标题和链接 |
| SEARCH | 跳过 | 搜索引擎源由关键词监控驱动，此处不处理 |

**入库前过滤：**

1. URL 去重（`selectArticleByUrl`）
2. **关键词匹配**：加载所有激活关键词，按来源语言分流
   - 中文源（`type=0`）→ 匹配中文关键词
   - 英文源（`type=1`）→ 匹配英文关键词（短词做单词边界检查）
   - 不匹配任何关键词 → 跳过，不入库
   - 匹配成功 → 自动关联 `keyword_id`
3. 无激活关键词时 → 全部放行（向后兼容）

**入库字段设置：**
- `source_id` = `source.id`
- `source_name` = `source.name`
- `fetch_origin` = `"SOURCE"`
- `keyword_id` = 匹配到的关键词 ID

**AI 分析：**
- PRIMARY 源 → `analyzeDeepAsync()`（深度分析：真假判断 + 相关性 + 重要性）
- 非 PRIMARY 源 → `analyzeAsync()`（基础分析：摘要 + 标签 + 情感 + 关键词）

---

## ② 关键词搜索（KEYWORD）

**触发条件：** `news_keyword` 表中 `is_active = 1` 的记录，且距上次抓取已超过 `fetch_interval` 分钟

**搜索流程：**

```
遍历每个激活关键词
    │
    ├── 查询扩展: AI 生成 2-3 个同义词/相关词
    │     └── expand() → QueryExpansionService → AiApiClient
    │
    ├── 多词并行搜索（使用扩展词列表）
    │     ├── Bing 搜索 (Jsoup 爬取 bing.com 搜索结果)
    │     ├── 搜狗搜索 (Jsoup 爬取 sogou.com 搜索结果)
    │     ├── B站搜索 (调用 B站公开 API)
    │     └── 微博热搜 (调用微博热搜 API，关键词匹配过滤)
    │           └── 英文短词做单词边界检查
    │
    ├── URL 去重合并
    │
    └── 标题必须包含原始关键词 → 否则不入库
          ├── 中文关键词 → 子串匹配
          └── 英文短词(<4字符) → 独立单词边界匹配
```

**入库字段设置：**
- `source_id` = NULL（不属于任何新闻源）
- `source_name` = 搜索引擎名称（"Bing搜索"/"搜狗搜索"/"B站搜索"/"微博热搜"）
- `fetch_origin` = `"KEYWORD"`
- `keyword_id` = 关键词 ID

**AI 分析：** 全部走 `analyzeDeepAsync(article, keyword, threshold)`

---

## ③ 去重 & 入库

- **去重依据：** `original_url`（通过 `selectArticleByUrl` 查询是否已存在）
- **通用字段：** `status="0"`（正常）、`is_pushed="0"`（未推送）、`read_count=0`、`fetch_time=sysdate()`
- **语言推断：** 来源 `type=1`（国外）→ `language="en"`，否则 `language="zh"`

### 两条入库路径对比

| 字段 | 新闻源抓取 (SOURCE) | 关键词搜索 (KEYWORD) |
|---|---|---|
| `source_id` | ✅ 有值（关联 news_source） | ❌ NULL |
| `source_name` | 新闻源名称 | 搜索引擎名称 |
| `keyword_id` | ✅ 匹配到的关键词 ID | ✅ 当前关键词 ID |
| `fetch_origin` | `"SOURCE"` | `"KEYWORD"` |
| 标题过滤 | 必须匹配激活关键词 | 必须包含原始关键词 |
| AI 分析类型 | PRIMARY→深度 / 其他→基础 | 全部深度分析 |

---

## ④ 异步 AI 分析

**线程池：** `aiAnalysisExecutor`（异步执行，不阻塞抓取流程）

**调用链：** `NewsAiAnalysisServiceImpl` → `AiApiClient.callNonStreaming()`

### 基础分析（`analyzeAsync`）

AI 返回 JSON：
```json
{
  "summary": "摘要",
  "tags": ["标签1", "标签2"],
  "sentiment": "positive|negative|neutral",
  "keywords": "关键词1,关键词2",
  "typeCode": "ai_tech"
}
```

填充字段：`summary`、`tags`、`sentiment`、`keywords`、`typeConfigId`

### 深度分析（`analyzeDeepAsync`）

在基础分析基础上增加：
```json
{
  "isReal": true/false,
  "relevance": 0-100,
  "relevanceReason": "理由",
  "keywordMentioned": true/false,
  "importance": "low|medium|high|urgent"
}
```

填充字段：`isReal`、`relevance`、`relevanceReason`、`keywordMentioned`、`importance`

---

## ⑤ 相关性阈值过滤

```java
// NewsFetcherServiceImpl.java
int threshold = kw.getRelevanceThreshold() != null ? kw.getRelevanceThreshold() : 60;

// NewsAiAnalysisServiceImpl.java — AI 分析完成后
if (relevanceThreshold > 0 && article.getRelevance() < relevanceThreshold) {
    article.setStatus("1");  // 下架，前端不可见
}
```

- 每个关键词可单独配置 `relevance_threshold`（默认 **60**）
- `relevance` 由 AI 模型综合评估（0-100）
- 相关性低于阈值 → `status="1"`（下架），用户看不到
- 低分文章仍保留在数据库中，不会在前端展示

---

## ⑥ WebSocket 实时推送

**推送时机：** 抓取完成后调用 `pushUnpushedArticles()`，查询 `is_pushed='0' AND status='0'` 的文章

**推送流程：**
```
pushUnpushedArticles()
    │
    ├── 查询未推送文章 (is_pushed=0, status=0)
    ├── 发布 NewsFetchedEvent 事件
    │     └── NewsWebSocketHandler.onNewsFetched() 监听
    │           └── broadcastArticle() 逐个广播
    │
    ├── 标记 is_pushed = '1'
    └── 记录推送日志 (news_push_log)
```

**WebSocket 消息格式：**
```json
{
  "type": "NEW_ARTICLE",
  "data": { /* NewsArticle 完整 JSON */ },
  "timestamp": 1718112000000
}
```

**心跳机制：** 客户端发送 `ping` → 服务端回复 `pong`

---

## ⑦ 前端展示

### 文章列表页 (`/news/article`)

| 列名 | 字段 | 说明 |
|---|---|---|
| 标题 | `title` | 可点击跳转原文 |
| 来源 | `sourceName` | SOURCE=新闻源名称 / KEYWORD=搜索引擎名称 |
| 来源方式 | `fetchOrigin` | SOURCE=新闻源 / KEYWORD=关键词 |
| 类型 | `typeName` | AI 分析后填充，关联 `news_type_config` |
| 语言 | `language` | zh/en |
| 情感 | `sentiment` | AI 分析后填充 |
| 推送 | `isPushed` | 已推送/未推送 |

### 大屏看板 (`/news/dashboard`)

- 总文章数、今日新增、国内/国外统计
- 24 小时时间线
- 来源分布饼图
- 情感分布饼图
- 热门标签 TOP15

### 一键解读（SSE 流式）

```
前端发起 → /news/article/interpret/{articleId}
    │
    ├── 服务端: AiApiClient.callStreaming() → 大模型流式输出
    ├── SSE 转发: Anthropic 格式 → OpenAI 兼容格式 → 前端
    └── 完成后落库 news_interpretation 表
```

---

## 关键词配置说明

`news_keyword` 表中每条记录：

| 配置项 | 作用 | 默认值 |
|---|---|---|
| `text` | 监控的关键词 | 必填 |
| `is_active` | 是否启用 | — |
| `fetch_interval` | 抓取间隔（分钟） | 30 |
| `relevance_threshold` | 相关性阈值，低于此值自动下架 | **60** |
| `expand_queries` | 手动扩展词（JSON 数组） | — |
| `category` | 分类标签 | — |

---

## 相关数据表

| 表名 | 用途 |
|---|---|
| `news_source` | 新闻源配置（RSS/API/CRAWL） |
| `news_keyword` | 监控关键词（含相关性阈值） |
| `news_article` | 新闻文章（核心表） |
| `news_type_config` | 新闻类型分类 |
| `news_prompt_config` | AI 提示词模板 |
| `news_model_config` | AI 模型配置（支持 OpenAI/Anthropic 双格式） |
| `news_interpretation` | AI 解读记录 |
| `news_push_log` | 推送日志 |
| `news_query_expansion` | 查询扩展词缓存 |

---

## 配置项

| 配置 | 位置 | 说明 |
|---|---|---|
| 抓取间隔 | `news.fetch-interval` | 默认 300000ms (5分钟) |
| AI 开关 | `news.ai.enabled` | 默认 true |
| 查询扩展开关 | `news.fetch.query-expansion.enabled` | 是否启用 AI 查询扩展 |
| 关键词默认阈值 | `news_keyword.relevance_threshold` | 默认 **60** |

---

## 字段填充时机汇总

| 字段 | 填充时机 | 来源 |
|---|---|---|
| `id` | 入库 | 自增 |
| `source_id` | 入库 | 新闻源抓取=source.id / 关键词搜索=NULL |
| `source_name` | 入库 | 源名称 或 搜索引擎名 |
| `title` | 入库 | RSS/API/搜索引擎返回 |
| `original_url` | 入库 | RSS/API/搜索引擎返回 |
| `summary` | 入库→AI覆盖 | 搜索引擎摘要 → AI 重写 |
| `content` | — | 暂未使用 |
| `language` | 入库 | 根据来源 type 推断 |
| `fetch_origin` | 入库 | `SOURCE` 或 `KEYWORD` |
| `keyword_id` | 入库 | SOURCE=匹配到的关键词 / KEYWORD=当前关键词 |
| `fetch_time` | 入库 | `sysdate()` |
| `publish_time` | 入库 | RSS/API 返回 |
| `tags` | AI 分析 | AI 返回 |
| `sentiment` | AI 分析 | AI 返回 |
| `keywords` | AI 分析 | AI 返回 |
| `type_config_id` | AI 分析 | typeCode → 匹配 news_type_config |
| `is_real` | AI 深度分析 | AI 返回 |
| `relevance` | AI 深度分析 | AI 评分 0-100 |
| `importance` | AI 深度分析 | AI 返回 |
| `status` | 入库 + AI分析后 | 0=正常, 1=下架（relevance < 60） |
| `is_pushed` | 入库 + 推送后 | 0→1 |

---

## AI 模型配置

系统支持 **OpenAI** 和 **Anthropic** 两种 API 格式，通过 `news_model_config.api_format` 字段切换。

| 格式 | API 路径 | 认证方式 | 示例 |
|---|---|---|---|
| `OPENAI` | `/v1/chat/completions` | `Authorization: Bearer <key>` | DeepSeek |
| `ANTHROPIC` | `/v1/messages` | `x-api-key` + `anthropic-version` | 火山引擎 CodingPlan |

核心工具类 `AiApiClient` 封装了两种格式的全部差异，4 个 AI 调用点（连接测试、AI 分析、查询扩展、SSE 解读）统一调用。
