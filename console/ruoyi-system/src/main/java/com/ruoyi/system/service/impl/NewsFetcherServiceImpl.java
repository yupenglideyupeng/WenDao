package com.ruoyi.system.service.impl;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
import com.ruoyi.common.event.NewsFetchedEvent;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.NewsArticle;
import com.ruoyi.system.domain.NewsPushLog;
import com.ruoyi.system.domain.NewsSource;
import com.ruoyi.system.mapper.NewsArticleMapper;
import com.ruoyi.system.service.INewsArticleService;
import com.ruoyi.system.service.INewsFetcherService;
import com.ruoyi.system.service.INewsPushLogService;
import com.ruoyi.system.service.INewsSourceService;

/**
 * 新闻抓取 服务层实现
 *
 * @author ruoyi
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

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 定时抓取新闻（每5分钟）
     */
    @Scheduled(fixedDelayString = "${news.fetch-interval:300000}")
    public void scheduledFetch()
    {
        log.info("定时抓取新闻任务开始");
        int count = fetchAllSources();
        log.info("定时抓取新闻任务完成，共抓取 {} 篇", count);
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
                int count = fetchFromSource(source);
                totalCount += count;
            }
            catch (Exception e)
            {
                log.error("抓取新闻源 [{}] 失败: {}", source.getName(), e.getMessage());
            }
        }
        // 推送未推送的文章
        pushUnpushedArticles(null);
        return totalCount;
    }

    @Override
    public int fetchFromSource(Long sourceId)
    {
        NewsSource source = newsSourceService.selectSourceById(sourceId);
        if (source == null)
        {
            return 0;
        }
        return fetchFromSource(source);
    }

    private int fetchFromSource(NewsSource source)
    {
        List<NewsArticle> articles;
        switch (source.getFetchType().toUpperCase())
        {
            case "RSS":
                articles = fetchRss(source);
                break;
            case "API":
                articles = fetchApi(source);
                break;
            default:
                log.warn("不支持的抓取方式: {} (来源: {})", source.getFetchType(), source.getName());
                return 0;
        }
        int count = 0;
        for (NewsArticle article : articles)
        {
            try
            {
                // 去重检查
                if (StringUtils.isNotEmpty(article.getOriginalUrl()))
                {
                    NewsArticle exist = articleMapper.selectArticleByUrl(article.getOriginalUrl());
                    if (exist != null)
                    {
                        continue;
                    }
                }
                article.setSourceId(source.getId());
                article.setSourceName(source.getName());
                article.setIsPushed("0");
                article.setReadCount(0);
                article.setStatus("0");
                // 根据来源类型设置语言
                if (StringUtils.isEmpty(article.getLanguage()))
                {
                    article.setLanguage("1".equals(source.getType()) ? "en" : "zh");
                }
                newsArticleService.insertArticle(article);
                count++;
            }
            catch (Exception e)
            {
                log.error("保存文章失败: {}", article.getTitle(), e);
            }
        }
        if (count > 0)
        {
            log.info("从 [{}] 抓取到 {} 篇新文章", source.getName(), count);
        }
        return count;
    }

    /**
     * 抓取RSS源
     */
    private List<NewsArticle> fetchRss(NewsSource source)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            ResponseEntity<String> response = restTemplate.getForEntity(source.getUrl(), String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(response.getBody())));
                NodeList items = doc.getElementsByTagName("item");
                if (items.getLength() == 0)
                {
                    items = doc.getElementsByTagName("entry");
                }
                int limit = Math.min(items.getLength(), 20);
                for (int i = 0; i < limit; i++)
                {
                    Element item = (Element) items.item(i);
                    NewsArticle article = new NewsArticle();
                    article.setTitle(getElementText(item, "title"));
                    article.setOriginalUrl(getElementText(item, "link"));
                    article.setSummary(getElementText(item, "description"));
                    if (StringUtils.isEmpty(article.getSummary()))
                    {
                        article.setSummary(getElementText(item, "summary"));
                    }
                    String pubDate = getElementText(item, "pubDate");
                    if (StringUtils.isEmpty(pubDate))
                    {
                        pubDate = getElementText(item, "published");
                    }
                    if (StringUtils.isNotEmpty(pubDate))
                    {
                        try
                        {
                            article.setPublishTime(parseDate(pubDate));
                        }
                        catch (Exception e)
                        {
                            article.setPublishTime(new Date());
                        }
                    }
                    else
                    {
                        article.setPublishTime(new Date());
                    }
                    articles.add(article);
                }
            }
        }
        catch (Exception e)
        {
            log.error("解析RSS失败: {}", source.getUrl(), e);
        }
        return articles;
    }

    /**
     * 抓取API源
     */
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
                if (configHeaders != null)
                {
                    configHeaders.forEach((k, v) -> headers.set(k, v.toString()));
                }
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(source.getUrl(), HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                articles = parseApiResponse(source, response.getBody());
            }
        }
        catch (Exception e)
        {
            log.error("抓取API失败: {}", source.getUrl(), e);
        }
        return articles;
    }

    private List<NewsArticle> parseApiResponse(NewsSource source, String body)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            if (source.getUrl().contains("hacker-news"))
            {
                // Hacker News: 返回的是ID数组，需要逐个获取
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
                            if (item.containsKey("url"))
                            {
                                article.setOriginalUrl(item.getString("url"));
                            }
                            else
                            {
                                article.setOriginalUrl("https://news.ycombinator.com/item?id=" + itemId);
                            }
                            if (item.containsKey("text"))
                            {
                                article.setSummary(item.getString("text"));
                            }
                            if (item.containsKey("time"))
                            {
                                article.setPublishTime(new Date(item.getLong("time") * 1000));
                            }
                            else
                            {
                                article.setPublishTime(new Date());
                            }
                            article.setLanguage("en");
                            articles.add(article);
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("获取Hacker News文章失败", e);
                    }
                }
            }
            else
            {
                // 通用JSON解析
                JSONObject json = JSON.parseObject(body);
                JSONArray dataList = json.getJSONArray("data");
                if (dataList == null)
                {
                    dataList = json.getJSONArray("items");
                }
                if (dataList == null)
                {
                    dataList = json.getJSONArray("list");
                }
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
                        {
                            article.setOriginalUrl(item.getString("link"));
                        }
                        article.setSummary(item.getString("summary"));
                        if (StringUtils.isEmpty(article.getSummary()))
                        {
                            article.setSummary(item.getString("description"));
                        }
                        if (StringUtils.isEmpty(article.getSummary()))
                        {
                            article.setSummary(item.getString("excerpt"));
                        }
                        article.setPublishTime(new Date());
                        articles.add(article);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("解析API响应失败", e);
        }
        return articles;
    }

    @Override
    public void pushUnpushedArticles(List<NewsArticle> articles)
    {
        if (articles == null)
        {
            articles = newsArticleService.selectUnpushedArticles();
        }
        if (articles == null || articles.isEmpty())
        {
            return;
        }
        // 发布事件，由ruoyi-framework中的WebSocket监听器处理
        eventPublisher.publishEvent(new NewsFetchedEvent(this, articles));
        // 记录推送日志
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

    private String getElementText(Element parent, String tagName)
    {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0)
        {
            return list.item(0).getTextContent();
        }
        return null;
    }

    private Date parseDate(String dateStr)
    {
        try
        {
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        }
        catch (Exception e)
        {
            return new Date();
        }
    }
}
