package com.wendao.system.service.impl;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.springframework.context.ApplicationEventPublisher;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wendao.system.event.NewsFetchedEvent;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.domain.NewsKeyword;
import com.wendao.system.domain.NewsPushLog;
import com.wendao.system.domain.NewsSource;
import com.wendao.system.mapper.NewsArticleMapper;
import com.wendao.system.service.INewsAiAnalysisService;
import com.wendao.system.service.INewsArticleService;
import com.wendao.system.service.INewsFetcherService;
import com.wendao.system.service.INewsKeywordService;
import com.wendao.system.service.INewsPushLogService;
import com.wendao.system.service.INewsSourceService;
import com.wendao.system.service.IQueryExpansionService;
import com.wendao.system.service.IWebSearchService;

/**
 * 新闻抓取 服务层实现
 * <p>
 * 统一由新闻源驱动：所有数据来源（RSS/API/CRAWL/SEARCH）均为 news_source 记录，
 * 关键词仅负责过滤，不再独立驱动搜索。
 *
 * @author wendao
 */
@Service
public class NewsFetcherServiceImpl implements INewsFetcherService
{
    private static final Logger log = LoggerFactory.getLogger(NewsFetcherServiceImpl.class);

    @Autowired
    private INewsSourceService newsSourceService;

    @Autowired
    private INewsArticleService newsArticleService;

    @Autowired
    private INewsPushLogService newsPushLogService;

    @Autowired
    private NewsArticleMapper articleMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private INewsAiAnalysisService aiAnalysisService;

    @Autowired
    private IWebSearchService webSearchService;

    @Autowired
    private INewsKeywordService keywordService;

    @Autowired
    private IQueryExpansionService queryExpansionService;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 定时抓取新闻（每5分钟）— 统一由 PRIMARY 新闻源驱动
     */
    @Scheduled(fixedDelayString = "${news.fetch-interval:300000}")
    public void scheduledFetch()
    {
        log.info("定时抓取新闻任务开始");
        int count = fetchPrimarySources();
        log.info("定时抓取完成，共 {} 篇新文章", count);
    }

    @Override
    public int fetchAllSources()
    {
        List<NewsSource> sources = newsSourceService.selectEnabledSources();
        int totalCount = 0;
        for (NewsSource source : sources)
        {
            try
            {
                totalCount += fetchFromSource(source);
            }
            catch (Exception e)
            {
                log.error("抓取新闻源 [{}] 失败: {}", source.getName(), e.getMessage());
            }
        }
        pushUnpushedArticles(null);
        return totalCount;
    }

    /**
     * 抓取所有 PRIMARY 模式源（RSS/API/CRAWL/SEARCH）
     */
    private int fetchPrimarySources()
    {
        List<NewsSource> sources = newsSourceService.selectPrimarySources();
        int totalCount = 0;
        for (NewsSource source : sources)
        {
            try
            {
                totalCount += fetchFromSource(source);
            }
            catch (Exception e)
            {
                log.error("抓取PRIMARY源 [{}] 失败: {}", source.getName(), e.getMessage());
            }
        }
        pushUnpushedArticles(null);
        return totalCount;
    }

    @Override
    public int fetchFromSource(Long sourceId)
    {
        NewsSource source = newsSourceService.selectSourceById(sourceId);
        if (source == null) return 0;
        return fetchFromSource(source);
    }

    /**
     * 从单个新闻源抓取文章
     * <p>
     * RSS/API/CRAWL 源：抓取后按关键词过滤标题，收集匹配文章 → 批量AI评分 → 过滤入库。
     * SEARCH 源：遍历激活关键词，每个关键词经 AI 扩展后调用指定搜索引擎，
     * 收集匹配文章 → 批量AI评分 → 过滤入库。
     */
    private int fetchFromSource(NewsSource source)
    {
        List<NewsArticle> articles;
        String fetchType = source.getFetchType().toUpperCase();

        if ("SEARCH".equals(fetchType))
        {
            return fetchSearchSource(source);
        }

        switch (fetchType)
        {
            case "RSS":   articles = fetchRss(source); break;
            case "API":   articles = fetchApi(source); break;
            case "CRAWL": articles = fetchCrawl(source); break;
            default:
                log.warn("不支持的抓取方式: {} (来源: {})", source.getFetchType(), source.getName());
                return 0;
        }

        if (articles.isEmpty()) return 0;

        // --- 关键词过滤（收集匹配文章）---
        List<NewsKeyword> activeKeywords = keywordService.selectActiveKeywords();
        boolean isForeignSource = "1".equals(source.getType());
        List<NewsKeyword> matchableKeywords = filterKeywordsByLanguage(activeKeywords, isForeignSource);

        List<NewsArticle> matched = new ArrayList<>();
        int maxArticles = source.getMaxArticlesPerFetch() != null ? source.getMaxArticlesPerFetch() : 10;

        for (NewsArticle article : articles)
        {
            if (matched.size() >= maxArticles) break;
            if (isDuplicate(article)) continue;

            List<NewsKeyword> kwMatches;
            if (!matchableKeywords.isEmpty())
            {
                kwMatches = matchKeywords(article.getTitle(), matchableKeywords, isForeignSource);
                if (kwMatches.isEmpty())
                {
                    log.debug("标题不匹配任何关键词，跳过: {}", article.getTitle());
                    continue;
                }
                article.setKeywordId(kwMatches.get(0).getId());
            }
            else
            {
                // 无激活关键词时跳过（不匹配任何关键词不入库）
                log.debug("无激活关键词，跳过: {}", article.getTitle());
                continue;
            }

            article.setSourceId(source.getId());
            article.setSourceName(source.getName());
            article.setIsPushed("0");
            article.setReadCount(0);
            article.setStatus("0");
            article.setFetchOrigin("SOURCE");
            article.setTags("[]");
            if (StringUtils.isEmpty(article.getLanguage()))
            {
                article.setLanguage(isForeignSource ? "en" : "zh");
            }
            matched.add(article);
        }

        if (matched.isEmpty()) return 0;

        // --- 批量 AI 评分 ---
        int threshold = getRelevanceThreshold(activeKeywords);
        List<INewsAiAnalysisService.RelevanceScore> scores = aiAnalysisService.batchScoreRelevance(matched);
        if (scores.isEmpty())
        {
            log.warn("批量AI评分失败，丢弃 [{}] 的 {} 篇匹配文章", source.getName(), matched.size());
            return 0;
        }

        // --- 按阈值过滤入库 ---
        int count = 0;
        for (int i = 0; i < matched.size() && i < scores.size(); i++)
        {
            NewsArticle article = matched.get(i);
            INewsAiAnalysisService.RelevanceScore score = scores.get(i);

            if (score.getRelevance() < threshold)
            {
                log.debug("相关性不足 ({} < {})，跳过: {}", score.getRelevance(), threshold, article.getTitle());
                continue;
            }

            try
            {
                article.setRelevance(score.getRelevance());
                article.setIsReal(score.isReal() ? 1 : 0);
                article.setImportance(score.getImportance());
                article.setRelevanceReason(score.getReason());

                newsArticleService.insertArticle(article);
                // 异步富文本分析（摘要、标签、情感、分类）
                aiAnalysisService.analyzeAsync(article);
                count++;
            }
            catch (Exception e)
            {
                log.error("保存文章失败: {}", article.getTitle(), e);
            }
        }

        if (count > 0)
        {
            log.info("从 [{}] 抓取到 {} 篇新文章（匹配 {} 篇，阈值 {}，通过 {} 篇）",
                    source.getName(), articles.size(), matched.size(), threshold, count);
        }
        return count;
    }

    /**
     * SEARCH 源抓取：遍历激活关键词 → AI 扩展 → 调用指定搜索引擎 → 收集匹配文章 → 批量评分 → 过滤入库
     */
    private int fetchSearchSource(NewsSource source)
    {
        List<NewsKeyword> keywords = keywordService.selectActiveKeywords();
        if (keywords.isEmpty())
        {
            log.debug("无激活关键词，跳过SEARCH源 [{}]", source.getName());
            return 0;
        }

        int maxArticles = source.getMaxArticlesPerFetch() != null ? source.getMaxArticlesPerFetch() : 20;

        // 1. 遍历关键词搜索，收集所有匹配文章
        List<NewsArticle> allMatched = new ArrayList<>();
        java.util.Set<String> seenUrls = new java.util.HashSet<>();

        for (NewsKeyword kw : keywords)
        {
            if (allMatched.size() >= maxArticles * 3) break; // 收集上限3倍，给评分后过滤留空间

            try
            {
                List<String> searchTerms = queryExpansionService.expand(kw.getText(), kw.getId());

                for (String term : searchTerms)
                {
                    if (allMatched.size() >= maxArticles * 3) break;

                    try
                    {
                        List<NewsArticle> results = webSearchService.searchByEngine(term, source.getName());
                        for (NewsArticle article : results)
                        {
                            if (allMatched.size() >= maxArticles * 3) break;
                            if (StringUtils.isEmpty(article.getTitle())) continue;
                            if (StringUtils.isNotEmpty(article.getOriginalUrl())
                                    && !seenUrls.add(article.getOriginalUrl())) continue;

                            if (!titleContainsKeyword(article.getTitle(), kw.getText())) continue;

                            article.setSourceId(source.getId());
                            article.setSourceName(source.getName());
                            article.setKeywordId(kw.getId());
                            article.setIsPushed("0");
                            article.setReadCount(0);
                            article.setStatus("0");
                            article.setFetchOrigin("SOURCE");
                            article.setTags("[]");
                            if (StringUtils.isEmpty(article.getLanguage()))
                            {
                                article.setLanguage("zh");
                            }
                            allMatched.add(article);
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("SEARCH源 [{}] 搜索词 [{}] 失败: {}", source.getName(), term, e.getMessage());
                    }
                }
            }
            catch (Exception e)
            {
                log.error("SEARCH源 [{}] 关键词 [{}] 处理失败: {}", source.getName(), kw.getText(), e.getMessage());
            }
        }

        if (allMatched.isEmpty()) return 0;

        // 2. 去重（按URL）
        java.util.LinkedHashMap<String, NewsArticle> deduped = new java.util.LinkedHashMap<>();
        for (NewsArticle a : allMatched)
        {
            String key = StringUtils.isNotEmpty(a.getOriginalUrl()) ? a.getOriginalUrl() : a.getTitle();
            deduped.putIfAbsent(key, a);
        }
        List<NewsArticle> uniqueArticles = new ArrayList<>(deduped.values());

        // 限制批量评分的文章数
        if (uniqueArticles.size() > maxArticles * 2)
        {
            uniqueArticles = uniqueArticles.subList(0, maxArticles * 2);
        }

        // 3. 批量 AI 评分
        int threshold = getRelevanceThreshold(keywords);
        List<INewsAiAnalysisService.RelevanceScore> scores = aiAnalysisService.batchScoreRelevance(uniqueArticles);
        if (scores.isEmpty())
        {
            log.warn("批量AI评分失败，丢弃SEARCH源 [{}] 的 {} 篇匹配文章", source.getName(), uniqueArticles.size());
            return 0;
        }

        // 4. 按阈值过滤入库
        int count = 0;
        for (int i = 0; i < uniqueArticles.size() && i < scores.size(); i++)
        {
            if (count >= maxArticles) break;

            NewsArticle article = uniqueArticles.get(i);
            INewsAiAnalysisService.RelevanceScore score = scores.get(i);

            if (score.getRelevance() < threshold)
            {
                log.debug("相关性不足 ({} < {})，跳过: {}", score.getRelevance(), threshold, article.getTitle());
                continue;
            }

            try
            {
                if (isDuplicate(article)) continue;

                article.setRelevance(score.getRelevance());
                article.setIsReal(score.isReal() ? 1 : 0);
                article.setImportance(score.getImportance());
                article.setRelevanceReason(score.getReason());

                newsArticleService.insertArticle(article);
                aiAnalysisService.analyzeAsync(article);
                count++;
            }
            catch (Exception e)
            {
                log.error("保存文章失败: {}", article.getTitle(), e);
            }
        }

        if (count > 0)
        {
            log.info("SEARCH源 [{}] 搜索完成，匹配 {} 篇去重 {} 篇，阈值 {}，通过 {} 篇",
                    source.getName(), allMatched.size(), uniqueArticles.size(), threshold, count);
        }
        return count;
    }

    /**
     * 获取相关性阈值：取所有激活关键词中阈值的最小值，默认 60
     */
    private int getRelevanceThreshold(List<NewsKeyword> keywords)
    {
        int minThreshold = 60;
        if (keywords != null)
        {
            for (NewsKeyword kw : keywords)
            {
                if (kw.getRelevanceThreshold() != null && kw.getRelevanceThreshold() > 0)
                {
                    if (minThreshold == 60 || kw.getRelevanceThreshold() < minThreshold)
                    {
                        minThreshold = kw.getRelevanceThreshold();
                    }
                }
            }
        }
        return minThreshold;
    }

    // ===================================================================
    // 抓取方法
    // ===================================================================

    private List<NewsArticle> fetchRss(NewsSource source)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            ResponseEntity<String> response = restTemplate.getForEntity(source.getUrl(), String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                String body = response.getBody();
                String trimmed = body.trim().toLowerCase();
                if (trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html"))
                {
                    log.warn("RSS源 [{}] 返回的是HTML页面而非RSS XML，跳过解析", source.getName());
                    return articles;
                }
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setFeature("http://xml.org/sax/features/validation", false);
                factory.setNamespaceAware(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(body)));
                NodeList items = doc.getElementsByTagName("item");
                if (items.getLength() == 0) items = doc.getElementsByTagName("entry");
                int limit = Math.min(items.getLength(), source.getMaxArticlesPerFetch() != null ? source.getMaxArticlesPerFetch() : 10);
                for (int i = 0; i < limit; i++)
                {
                    Element item = (Element) items.item(i);
                    NewsArticle article = new NewsArticle();
                    article.setTitle(getElementText(item, "title"));
                    // 处理 link：RSS <link>text</link>，Atom <link href="..."/>
                    String linkText = getElementText(item, "link");
                    if (StringUtils.isEmpty(linkText))
                    {
                        // Atom 格式：<link href="..."/> 或 <link rel="alternate" href="..."/>
                        NodeList linkNodes = item.getElementsByTagName("link");
                        if (linkNodes.getLength() > 0)
                        {
                            Element linkEl = (Element) linkNodes.item(0);
                            linkText = linkEl.getAttribute("href");
                        }
                    }
                    article.setOriginalUrl(linkText);
                    article.setSummary(getElementText(item, "description"));
                    if (StringUtils.isEmpty(article.getSummary()))
                        article.setSummary(getElementText(item, "summary"));
                    String pubDate = getElementText(item, "pubDate");
                    if (StringUtils.isEmpty(pubDate)) pubDate = getElementText(item, "published");
                    article.setPublishTime(StringUtils.isNotEmpty(pubDate) ? parseDate(pubDate) : new Date());
                    articles.add(article);
                }
            }
        }
        catch (Exception e) { log.error("解析RSS失败: {}", source.getUrl(), e); }
        return articles;
    }

    private List<NewsArticle> fetchApi(NewsSource source)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (source.getFetchConfig() != null)
            {
                JSONObject config = JSON.parseObject(source.getFetchConfig());
                JSONObject configHeaders = config.getJSONObject("headers");
                if (configHeaders != null) configHeaders.forEach((k, v) -> headers.set(k, v.toString()));
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(source.getUrl(), HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                articles = parseApiResponse(source, response.getBody());
            }
        }
        catch (Exception e) { log.error("抓取API失败: {}", source.getUrl(), e); }
        return articles;
    }

    private List<NewsArticle> parseApiResponse(NewsSource source, String body)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            if (source.getUrl().contains("hacker-news"))
            {
                JSONArray ids = JSON.parseArray(body);
                int limit = Math.min(ids.size(), 10);
                for (int i = 0; i < limit; i++)
                {
                    try
                    {
                        Long itemId = ids.getLong(i);
                        String itemUrl = "https://hacker-news.firebaseio.com/v0/item/" + itemId + ".json";
                        ResponseEntity<String> itemResp = restTemplate.getForEntity(itemUrl, String.class);
                        if (itemResp.getStatusCode().is2xxSuccessful() && itemResp.getBody() != null)
                        {
                            JSONObject item = JSON.parseObject(itemResp.getBody());
                            NewsArticle article = new NewsArticle();
                            article.setTitle(item.getString("title"));
                            article.setOriginalUrl(item.containsKey("url") ? item.getString("url")
                                    : "https://news.ycombinator.com/item?id=" + itemId);
                            if (item.containsKey("text")) article.setSummary(item.getString("text"));
                            article.setPublishTime(item.containsKey("time")
                                    ? new Date(item.getLong("time") * 1000) : new Date());
                            article.setLanguage("en");
                            articles.add(article);
                        }
                    }
                    catch (Exception e) { log.error("获取Hacker News文章失败", e); }
                }
            }
            else if (source.getUrl().contains("zhihu"))
            {
                JSONObject json = JSON.parseObject(body);
                JSONArray dataList = json.getJSONArray("data");
                if (dataList != null)
                {
                    int limit = Math.min(dataList.size(), 20);
                    for (int i = 0; i < limit; i++)
                    {
                        try
                        {
                            JSONObject item = dataList.getJSONObject(i);
                            JSONObject target = item.getJSONObject("target");
                            if (target == null) continue;
                            NewsArticle article = new NewsArticle();
                            article.setTitle(target.getString("title"));
                            String url = target.getString("url");
                            article.setOriginalUrl(StringUtils.isNotEmpty(url) ? url
                                    : "https://www.zhihu.com/search?type=content&q="
                                            + java.net.URLEncoder.encode(article.getTitle(), "UTF-8"));
                            article.setSummary(target.getString("excerpt"));
                            article.setPublishTime(new Date());
                            article.setLanguage("zh");
                            if (StringUtils.isNotEmpty(article.getTitle())) articles.add(article);
                        }
                        catch (Exception e) { log.error("解析知乎热榜条目失败", e); }
                    }
                }
            }
            else if (source.getUrl().contains("weibo"))
            {
                JSONObject json = JSON.parseObject(body);
                JSONObject data = json.getJSONObject("data");
                if (data != null)
                {
                    JSONArray realtime = data.getJSONArray("realtime");
                    if (realtime != null)
                    {
                        int limit = Math.min(realtime.size(), 20);
                        for (int i = 0; i < limit; i++)
                        {
                            try
                            {
                                JSONObject item = realtime.getJSONObject(i);
                                String word = item.getString("word");
                                if (StringUtils.isEmpty(word)) continue;
                                NewsArticle article = new NewsArticle();
                                article.setTitle(word);
                                article.setOriginalUrl("https://s.weibo.com/weibo?q=%23"
                                        + java.net.URLEncoder.encode(word, "UTF-8") + "%23");
                                article.setSummary("热搜热度: " + item.getLongValue("num")
                                        + (StringUtils.isNotEmpty(item.getString("label_name"))
                                        ? " [" + item.getString("label_name") + "]" : ""));
                                article.setPublishTime(new Date());
                                article.setLanguage("zh");
                                articles.add(article);
                            }
                            catch (Exception e) { log.error("解析微博热搜条目失败", e); }
                        }
                    }
                }
            }
            else
            {
                JSONObject json = JSON.parseObject(body);
                JSONArray dataList = json.getJSONArray("data");
                if (dataList == null) dataList = json.getJSONArray("items");
                if (dataList == null) dataList = json.getJSONArray("list");
                if (dataList != null)
                {
                    int limit = Math.min(dataList.size(), 20);
                    for (int i = 0; i < limit; i++)
                    {
                        JSONObject item = dataList.getJSONObject(i);
                        NewsArticle article = new NewsArticle();
                        article.setTitle(item.getString("title"));
                        article.setOriginalUrl(item.getString("url"));
                        if (StringUtils.isEmpty(article.getOriginalUrl()))
                            article.setOriginalUrl(item.getString("link"));
                        article.setSummary(item.getString("summary"));
                        if (StringUtils.isEmpty(article.getSummary()))
                            article.setSummary(item.getString("description"));
                        if (StringUtils.isEmpty(article.getSummary()))
                            article.setSummary(item.getString("excerpt"));
                        article.setPublishTime(new Date());
                        articles.add(article);
                    }
                }
            }
        }
        catch (Exception e) { log.error("解析API响应失败", e); }
        return articles;
    }

    private List<NewsArticle> fetchCrawl(NewsSource source)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(source.getUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000).get();
            org.jsoup.select.Elements links = doc.select("a[href]");
            java.util.Set<String> seenUrls = new java.util.HashSet<>();
            for (org.jsoup.nodes.Element link : links)
            {
                String title = link.text().trim();
                String url = link.absUrl("href");
                if (title.length() < 10 || title.length() > 200) continue;
                if (!url.startsWith("http")) continue;
                if (seenUrls.contains(url)) continue;
                seenUrls.add(url);
                NewsArticle article = new NewsArticle();
                article.setTitle(title);
                article.setOriginalUrl(url);
                article.setPublishTime(new Date());
                articles.add(article);
                if (articles.size() >= (source.getMaxArticlesPerFetch() != null ? source.getMaxArticlesPerFetch() : 10)) break;
            }
            log.info("CRAWL抓取 [{}] 获取 {} 条", source.getName(), articles.size());
        }
        catch (Exception e) { log.error("CRAWL抓取失败: {} - {}", source.getName(), e.getMessage()); }
        return articles;
    }

    // ===================================================================
    // 推送
    // ===================================================================

    @Override
    public void pushUnpushedArticles(List<NewsArticle> articles)
    {
        if (articles == null)
        {
            articles = newsArticleService.selectUnpushedArticles();
        }
        if (articles == null || articles.isEmpty()) return;

        eventPublisher.publishEvent(new NewsFetchedEvent(this, articles));
        for (NewsArticle article : articles)
        {
            try
            {
                newsArticleService.markAsPushed(article.getId());
                NewsPushLog pushLog = new NewsPushLog();
                pushLog.setArticleId(article.getId());
                pushLog.setPushStatus("0");
                pushLog.setPushType("WEBSOCKET");
                pushLog.setPushTime(new Date());
                newsPushLogService.insertPushLog(pushLog);
            }
            catch (Exception e)
            {
                log.error("记录推送日志失败: {}", article.getTitle(), e);
                NewsPushLog pushLog = new NewsPushLog();
                pushLog.setArticleId(article.getId());
                pushLog.setPushStatus("1");
                pushLog.setPushType("WEBSOCKET");
                pushLog.setErrorMsg(e.getMessage());
                pushLog.setPushTime(new Date());
                newsPushLogService.insertPushLog(pushLog);
            }
        }
    }

    // ===================================================================
    // 工具方法
    // ===================================================================

    private boolean isDuplicate(NewsArticle article)
    {
        if (StringUtils.isNotEmpty(article.getOriginalUrl()))
        {
            NewsArticle exist = articleMapper.selectArticleByUrl(article.getOriginalUrl());
            return exist != null;
        }
        return false;
    }

    private String getElementText(Element parent, String tagName)
    {
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? list.item(0).getTextContent() : null;
    }

    private Date parseDate(String dateStr)
    {
        try
        {
            LocalDateTime ldt = LocalDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        }
        catch (Exception e) { return new Date(); }
    }

    /**
     * 按来源语言过滤匹配关键词列表
     * <p>
     * 国内源：匹配所有关键词（中英文都匹配，因为中文科技新闻中常出现 AI、GPT 等英文词）
     * 国外源：只匹配非中文关键词
     */
    private List<NewsKeyword> filterKeywordsByLanguage(List<NewsKeyword> keywords, boolean isForeignSource)
    {
        List<NewsKeyword> result = new ArrayList<>();
        if (keywords == null) return result;
        for (NewsKeyword kw : keywords)
        {
            boolean kwIsChinese = containsChinese(kw.getText());
            // 国内源：中英文关键词都保留
            if (!isForeignSource) result.add(kw);
            // 国外源：只保留非中文关键词
            else if (!kwIsChinese) result.add(kw);
        }
        return result;
    }

    /**
     * 关键词匹配：返回标题匹配到的所有关键词
     */
    private List<NewsKeyword> matchKeywords(String title, List<NewsKeyword> keywords, boolean foreignSource)
    {
        List<NewsKeyword> matched = new ArrayList<>();
        if (StringUtils.isEmpty(title) || keywords.isEmpty()) return matched;

        String titleLower = foreignSource ? title.toLowerCase() : title;
        for (NewsKeyword kw : keywords)
        {
            String kwText = kw.getText();
            if (StringUtils.isEmpty(kwText)) continue;
            if (foreignSource)
            {
                if (titleLower.contains(kwText.toLowerCase())) matched.add(kw);
            }
            else
            {
                if (title.contains(kwText)) matched.add(kw);
            }
        }
        return matched;
    }

    private boolean containsChinese(String text)
    {
        if (StringUtils.isEmpty(text)) return false;
        for (char c : text.toCharArray())
        {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    /**
     * 检查标题是否包含原始关键词
     */
    private boolean titleContainsKeyword(String title, String keyword)
    {
        if (StringUtils.isEmpty(title) || StringUtils.isEmpty(keyword)) return false;

        String kwLower = keyword.toLowerCase();
        boolean kwIsChinese = containsChinese(keyword);

        if (kwIsChinese) return title.contains(keyword);

        if (keyword.length() < 4)
        {
            String titleLower = title.toLowerCase();
            int idx = titleLower.indexOf(kwLower);
            while (idx >= 0)
            {
                boolean startOk = idx == 0 || !Character.isLetter(titleLower.charAt(idx - 1));
                boolean endOk = idx + kwLower.length() >= titleLower.length()
                        || !Character.isLetter(titleLower.charAt(idx + kwLower.length()));
                if (startOk && endOk) return true;
                idx = titleLower.indexOf(kwLower, idx + 1);
            }
            return false;
        }
        return title.toLowerCase().contains(kwLower);
    }
}
