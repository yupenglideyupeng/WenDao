package com.wendao.system.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.service.IWebSearchService;

/**
 * 网页搜索服务实现（Jsoup + RestTemplate）
 *
 * @author wendao
 */
@Service
public class WebSearchServiceImpl implements IWebSearchService
{
    private static final Logger log = LoggerFactory.getLogger(WebSearchServiceImpl.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    };

    private String randomUA()
    {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    @Override
    public List<NewsArticle> searchByKeyword(String keyword)
    {
        List<NewsArticle> allResults = new ArrayList<>();

        // 并行搜索各个来源
        try { allResults.addAll(searchBing(keyword)); } catch (Exception e) { log.warn("Bing搜索失败: {}", e.getMessage()); }
        try { allResults.addAll(searchSogou(keyword)); } catch (Exception e) { log.warn("搜狗搜索失败: {}", e.getMessage()); }
        try { allResults.addAll(searchBilibili(keyword)); } catch (Exception e) { log.warn("B站搜索失败: {}", e.getMessage()); }
        try { allResults.addAll(searchWeibo(keyword)); } catch (Exception e) { log.warn("微博热搜匹配失败: {}", e.getMessage()); }

        log.info("关键词 [{}] 搜索完成，共获取 {} 条结果", keyword, allResults.size());
        return allResults;
    }

    // ==================== Bing 搜索 ====================

    private List<NewsArticle> searchBing(String keyword)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            String url = "https://www.bing.com/search?q=" + keyword + "&count=20";
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUA())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(15000)
                    .get();

            Elements results = doc.select("li.b_algo");
            for (Element item : results)
            {
                Element titleEl = item.selectFirst("h2 a");
                if (titleEl == null) continue;

                String title = titleEl.text().trim();
                String link = titleEl.attr("href");
                Element snippetEl = item.selectFirst(".b_caption p");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                if (StringUtils.isNotEmpty(title) && StringUtils.isNotEmpty(link) && link.startsWith("http"))
                {
                    NewsArticle article = new NewsArticle();
                    article.setTitle(title);
                    article.setOriginalUrl(link);
                    article.setSummary(snippet);
                    article.setSourceName("Bing搜索");
                    article.setLanguage("zh");
                    article.setPublishTime(new Date());
                    articles.add(article);
                }
            }
            log.info("Bing搜索 [{}] 获取 {} 条", keyword, articles.size());
        }
        catch (IOException e)
        {
            log.warn("Bing搜索异常: {}", e.getMessage());
        }
        return articles;
    }

    // ==================== 搜狗搜索 ====================

    private List<NewsArticle> searchSogou(String keyword)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            String url = "https://www.sogou.com/web?query=" + keyword + "&ie=utf-8";
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUA())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(15000)
                    .get();

            Elements results = doc.select(".vrwrap, .rb");
            for (Element item : results)
            {
                Element titleEl = item.selectFirst("h3 a, .vr-title a, .vrTitle a");
                if (titleEl == null) continue;

                String title = titleEl.text().trim();
                String link = titleEl.attr("href");
                Element snippetEl = item.selectFirst(".space-txt, .str-text-info, .str_info, .text-layout");
                if (snippetEl == null) snippetEl = item.selectFirst("p");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                if (StringUtils.isNotEmpty(title) && !title.contains("大家还在搜"))
                {
                    NewsArticle article = new NewsArticle();
                    article.setTitle(title);
                    article.setOriginalUrl(StringUtils.isNotEmpty(link) ? link : "");
                    article.setSummary(snippet);
                    article.setSourceName("搜狗搜索");
                    article.setLanguage("zh");
                    article.setPublishTime(new Date());
                    articles.add(article);
                }
            }
            log.info("搜狗搜索 [{}] 获取 {} 条", keyword, articles.size());
        }
        catch (IOException e)
        {
            log.warn("搜狗搜索异常: {}", e.getMessage());
        }
        return articles;
    }

    // ==================== B站搜索（公开API） ====================

    private List<NewsArticle> searchBilibili(String keyword)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            String url = "https://api.bilibili.com/x/web-interface/search/type"
                    + "?keyword=" + keyword + "&search_type=video&order=pubdate&page=1&pagesize=20";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", randomUA());
            headers.set("Referer", "https://search.bilibili.com/");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JSONObject json = JSON.parseObject(response.getBody());
            if (json.getInteger("code") == 0)
            {
                JSONObject data = json.getJSONObject("data");
                JSONArray result = data != null ? data.getJSONArray("result") : null;
                if (result != null)
                {
                    for (int i = 0; i < result.size(); i++)
                    {
                        JSONObject video = result.getJSONObject(i);
                        NewsArticle article = new NewsArticle();
                        String title = video.getString("title");
                        article.setTitle(title != null ? title.replaceAll("<[^>]+>", "") : "");
                        article.setOriginalUrl("https://www.bilibili.com/video/" + video.getString("bvid"));
                        article.setSummary(video.getString("description"));
                        article.setSourceName("B站搜索");
                        article.setLanguage("zh");
                        Long pubdate = video.getLong("pubdate");
                        article.setPublishTime(pubdate != null ? new Date(pubdate * 1000) : new Date());
                        article.setReadCount(video.getInteger("play"));
                        articles.add(article);
                    }
                }
            }
            log.info("B站搜索 [{}] 获取 {} 条", keyword, articles.size());
        }
        catch (Exception e)
        {
            log.warn("B站搜索异常: {}", e.getMessage());
        }
        return articles;
    }

    // ==================== 微博热搜 ====================

    private List<NewsArticle> searchWeibo(String keyword)
    {
        List<NewsArticle> articles = new ArrayList<>();
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", randomUA());
            headers.set("Referer", "https://weibo.com/");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://weibo.com/ajax/side/hotSearch",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JSONObject json = JSON.parseObject(response.getBody());
            if (json.getInteger("ok") == 1)
            {
                JSONObject data = json.getJSONObject("data");
                JSONArray realtime = data != null ? data.getJSONArray("realtime") : null;
                if (realtime != null)
                {
                    String kwLower = keyword.toLowerCase();
                    for (int i = 0; i < realtime.size(); i++)
                    {
                        JSONObject item = realtime.getJSONObject(i);
                        String word = item.getString("word");
                        String note = item.getString("note");
                        String topicName = StringUtils.isNotEmpty(note) ? note : word;

                        if (topicName == null) continue;
                        String topicLower = topicName.toLowerCase();

                        // 关键词匹配
                        if (topicLower.contains(kwLower) || kwLower.contains(topicLower))
                        {
                            NewsArticle article = new NewsArticle();
                            article.setTitle("🔥 " + topicName);
                            article.setOriginalUrl("https://s.weibo.com/weibo?q="
                                    + "%23" + topicName + "%23");
                            article.setSummary("微博热搜话题「" + topicName
                                    + "」，热度 " + (item.getLong("num") != null
                                    ? String.valueOf(item.getLong("num")) : "未知"));
                            article.setSourceName("微博热搜");
                            article.setLanguage("zh");
                            article.setPublishTime(new Date());
                            article.setReadCount(item.getInteger("raw_hot"));
                            articles.add(article);
                        }
                    }
                }
            }
            log.info("微博热搜匹配 [{}] 获取 {} 条", keyword, articles.size());
        }
        catch (Exception e)
        {
            log.warn("微博热搜异常: {}", e.getMessage());
        }
        return articles;
    }
}
