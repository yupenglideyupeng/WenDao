# 新闻采集 → 展示 完整工作流程

## 架构概览

```
定时任务 (每5分钟)
    │
    └── fetchPrimarySources() — 遍历所有 PRIMARY 模式新闻源
         │
         ├── RSS 源   → fetchRss()   → 解析 XML <item>/<entry>
         ├── API 源   → fetchApi()   → REST API JSON 解析
         ├── CRAWL 源 → fetchCrawl() → Jsoup HTML 爬取
         └── SEARCH 源 → fetchSearchSource()
              └── 遍历激活关键词 → AI查询扩展 → searchByEngine()
                   ├── Bing 搜索 (Jsoup)
                   ├── 搜狗搜索 (Jsoup)
                   ├── B站搜索 (B站公开API)
                   └── 微博热搜 (微博API + 关键词匹配)
                        │
                        ▼
                 ① 标题关键词匹配过滤（三层过滤第一层）
                        │
                        ▼
                 ② 批量 AI 相关性评分 batchScoreRelevance()
                        │
                        ▼
                 ③ 阈值过滤 → relevance < 阈值 → 丢弃（三层过滤第二层）
                        │
                        ▼
                 ④ 入库 news_article
                        │
                        ▼
                 ⑤ 异步 AI 分析 analyzeAsync()（摘要/标签/情感/分类）
                        │
                        ▼
                 ⑥ WebSocket 实时推送 NEW_ARTICLE
                        │
                        ▼
                 ⑦ 前端展示（文章列表 / 大屏看板 / 一键解读 SSE）
```

---

## 三层过滤机制

```
第一层：标题关键词匹配（入库前，代码层面）
    │
    ├── 中文源(type=0) → 匹配中文关键词（子串匹配）
    ├── 英文源(type=1) → 匹配英文关键词（子串 + 短词单词边界）
    └── 不匹配任何关键词 → 直接丢弃，不入库
    │
    ▼
第二层：批量 AI 相关性评分（入库前，同步调用）
    │
    ├── batchScoreRelevance() → 一次 AI 调用评 N 篇
    ├── 返回 relevance(0-100)、isReal、importance
    └── AI 不可用时默认放行（60分）
    │
    ▼
第三层：相关性阈值过滤（入库前）
    │
    ├── relevance >= threshold → 入库
    └── relevance < threshold  → 丢弃
```

> 默认阈值为 **60**（取所有激活关键词阈值中的最小值）。

### 英文短词单词边界匹配

关键词长度 < 4 个字符时（如 "AI"、"GPT"），使用单词边界匹配，防止子串误匹配：

| 标题 | 关键词 "AI" | 匹配结果 |
|---|---|---|
| "New AI breakthrough announced" | ✅ 匹配 | AI 是独立单词 |
| "DETAIL report released" | ❌ 不匹配 | "DETAIL" 中的 "AI" 不是独立单词 |
| "SHANGHAI news today" | ❌ 不匹配 | "SHANGHAI" 中的 "AI" 不是独立单词 |

---

## ① 新闻源抓取

**触发条件：** `news_source` 表中 `fetch_mode = 'PRIMARY'` 且 `status = '0'`

### 按 fetch_type 分发

| fetch_type | 说明 | 实现方法 |
|---|---|---|
| RSS | 解析 RSS/Atom XML | `fetchRss()` — 提取 title/link/description/pubDate |
| API | 调用 REST API 获取 JSON | `fetchApi()` — 支持 HackerNews、知乎热榜、微博热搜、通用 JSON |
| CRAWL | Jsoup 爬取 HTML | `fetchCrawl()` — 提取页面中 `<a>` 标签的标题和链接 |
| SEARCH | 关键词搜索 | `fetchSearchSource()` — 遍历关键词 → AI扩展 → 多引擎搜索 |

### RSS/API/CRAWL 源入库前处理

1. URL 去重（`selectArticleByUrl`）
2. 关键词过滤：按来源语言分流
   - 中文源（`type=0`）→ 匹配中文关键词
   - 英文源（`type=1`）→ 匹配英文关键词（短词做单词边界检查）
   - 无激活关键词 → 跳过，不入库
3. 批量 AI 评分 → 阈值过滤
4. 入库字段设置：
   - `source_id` = `source.id`
   - `source_name` = `source.name`
   - `fetch_origin` = `"SOURCE"`
   - `keyword_id` = 匹配到的关键词 ID

### SEARCH 源处理流程

1. 遍历激活关键词
2. 每个关键词通过 AI 查询扩展生成搜索词
3. 每个搜索词调用指定搜索引擎
4. 标题必须包含原始关键词（英文短词做单词边界检查）
5. URL 去重合并
6. 批量 AI 评分 → 阈值过滤 → 入库

**入库字段设置：**
- `source_id` = SEARCH 源 ID
- `source_name` = SEARCH 源名称（"Bing搜索"/"搜狗搜索"/"B站搜索"/"微博热搜"）
- `fetch_origin` = `"SOURCE"`
- `keyword_id` = 关键词 ID

---

## ② 批量 AI 相关性评分

**调用链：** `NewsFetcherServiceImpl` → `INewsAiAnalysisService.batchScoreRelevance()`

一次 AI 调用评多篇文章（同步，入库前）：

```json
[
  {"index": 0, "relevance": 85, "isReal": true, "importance": "high", "reason": "..."},
  {"index": 1, "relevance": 30, "isReal": true, "importance": "low", "reason": "..."}
]
```

- AI 未启用或无可用模型 → 默认全部 60 分放行
- AI 评分失败 → 返回空列表，丢弃所有文章

---

## ③ 异步 AI 分析（入库后）

**线程池：** `aiAnalysisExecutor`（异步，不阻塞抓取流程）

**调用链：** `NewsAiAnalysisServiceImpl` → `AiApiClient.callNonStreaming()`

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

---

## ④ WebSocket 实时推送

**推送时机：** 抓取完成后调用 `pushUnpushedArticles()`，查询 `is_pushed='0' AND status='0'` 的文章

**推送流程：**
```
pushUnpushedArticles()
    │
    ├── 查询未推送文章
    ├── 发布 NewsFetchedEvent 事件
    │     └── NewsWebSocketHandler.onNewsFetched() 监听
    │           └── broadcastArticle() 逐个广播
    ├── 标记 is_pushed = '1'
    └── 记录推送日志 news_push_log
```

**消息格式：**
```json
{
  "type": "NEW_ARTICLE",
  "data": { /* NewsArticle 完整 JSON */ },
  "timestamp": 1718112000000
}
```

---

## ⑤ 一键解读（SSE 流式）

```
前端发起 → GET /news/article/interpret/{articleId}?token=xxx
    │
    ├── 服务端: AiApiClient.callStreaming() → 大模型流式输出
    ├── SSE 转发: Anthropic 格式 → OpenAI 兼容格式 → 前端
    └── 完成后发送 done 事件（含 modelName）
```

> 解读为纯实时流式，不持久化到数据库。每次打开对话框即开始新解读。

---

## 国内外来源统计

Dashboard 统计 SQL 两层判断：

| 条件 | 归类 |
|---|---|
| `source_id` 有值，关联 `news_source.type='0'` | 国内 |
| `source_id` 有值，关联 `news_source.type='1'` | 国外 |
| `source_id` 为空，`language='en'` | 国外（兜底） |
| `source_id` 为空，`language` 为其他/NULL | 国内（兜底） |

确保 `domesticCount + foreignCount = totalArticles`。

---

## 相关数据表

| 表名 | 用途 |
|---|---|
| `news_source` | 新闻源配置（RSS/API/CRAWL/SEARCH） |
| `news_keyword` | 监控关键词（含相关性阈值） |
| `news_article` | 新闻文章（核心表） |
| `news_type_config` | 新闻类型分类 |
| `news_prompt_config` | AI 提示词模板 |
| `news_model_config` | AI 模型配置（支持 OpenAI/Anthropic 双格式） |
| `news_push_log` | 推送日志 |
| `news_query_expansion` | 查询扩展词缓存 |

---

## 配置项参考

| 配置 | 位置 | 说明 |
|---|---|---|
| 抓取间隔 | `news.fetch-interval` | 默认 300000ms (5分钟) |
| AI 开关 | `news.ai.enabled` | 默认 true |
| AES 密钥 | `wendao.aes-key` | 32字符，加密 API Key |
| SSE 超时 | `spring.mvc.async.request-timeout` | 默认 300000ms (5分钟) |
| 关键词阈值 | `news_keyword.relevance_threshold` | 默认 60（最低60） |

---

## 字段填充时机

| 字段 | 填充时机 | 来源 |
|---|---|---|
| `id` | 入库 | 自增 |
| `source_id` | 入库 | 关联 news_source.id，SEARCH 源也有关联 |
| `source_name` | 入库 | 源名称 或 搜索引擎名 |
| `title` | 入库 | RSS/API/搜索引擎返回 |
| `original_url` | 入库 | RSS/API/搜索引擎返回 |
| `summary` | AI 分析 | AI 重写 |
| `language` | 入库 | 根据来源 type 推断（国内=zh, 国外=en） |
| `fetch_origin` | 入库 | `SOURCE` |
| `keyword_id` | 入库 | 匹配到的关键词 ID |
| `fetch_time` | 入库 | `sysdate()` |
| `tags` | AI 分析 | AI 返回 |
| `sentiment` | AI 分析 | AI 返回（positive/negative/neutral） |
| `keywords` | AI 分析 | AI 返回 |
| `type_config_id` | AI 分析 | typeCode → 匹配 news_type_config |
| `relevance` | 入库前评分 | AI 批量评分 0-100 |
| `is_real` | 入库前评分 | AI 批量评分 |
| `importance` | 入库前评分 | AI 批量评分 |
| `status` | 入库 | 0=正常展示 |
| `is_pushed` | 入库 + 推送后 | 0→1 |
