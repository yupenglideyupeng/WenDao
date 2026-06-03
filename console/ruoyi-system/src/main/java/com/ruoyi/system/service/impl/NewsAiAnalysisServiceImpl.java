package com.ruoyi.system.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.NewsAiProperties;
import com.ruoyi.system.domain.NewsArticle;
import com.ruoyi.system.service.INewsAiAnalysisService;
import com.ruoyi.system.service.INewsArticleService;

/**
 * AI新闻分析 服务层实现
 *
 * @author ruoyi
 */
@Service
public class NewsAiAnalysisServiceImpl implements INewsAiAnalysisService
{
    private static final Logger log = LoggerFactory.getLogger(NewsAiAnalysisServiceImpl.class);

    @Autowired
    private NewsAiProperties properties;

    @Autowired
    private INewsArticleService newsArticleService;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT =
        "你是一个专业的新闻分析助手。你的任务是对用户提供的新闻文章进行分析，并返回严格的JSON格式结果。\n\n" +
        "分析要求：\n" +
        "1. summary：用中文撰写一段80-150字的简洁摘要，概括新闻核心内容\n" +
        "2. tags：提取3-5个标签，以JSON字符串数组形式返回，例如 [\"AI\",\"科技\",\"政策\"]\n" +
        "3. sentiment：判断新闻的情感倾向，仅返回以下三个值之一：positive（积极）、negative（消极）、neutral（中性）\n" +
        "4. keywords：提取3-5个核心关键词，以逗号分隔的字符串形式返回，例如 \"AI,DeepSeek,大模型\"\n\n" +
        "输出格式要求：\n" +
        "你必须仅返回一个合法的JSON对象，不要包含任何其他文字、解释或markdown代码块标记。格式如下：\n" +
        "{\"summary\":\"摘要内容\",\"tags\":[\"标签1\",\"标签2\"],\"sentiment\":\"positive\",\"keywords\":\"关键词1,关键词2\"}\n\n" +
        "如果文章内容为空或无法分析，返回：\n" +
        "{\"summary\":\"\",\"tags\":[],\"sentiment\":\"neutral\",\"keywords\":\"\"}";

    @Async("aiAnalysisExecutor")
    @Override
    public void analyzeAsync(NewsArticle article)
    {
        // 未启用或未配置API Key，跳过
        if (!properties.isEnabled() || StringUtils.isEmpty(properties.getApiKey()))
        {
            log.debug("AI分析未启用或未配置API Key，跳过文章 [{}]", article.getTitle());
            return;
        }

        try
        {
            // 构建请求体
            JSONObject requestBody = buildRequestBody(article);

            // 发送HTTP请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + properties.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    properties.getApiUrl(), entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful())
            {
                log.error("AI分析请求失败，状态码: {}，文章: {}",
                        response.getStatusCode().value(), article.getTitle());
                return;
            }

            // 解析响应
            JSONObject respJson = JSON.parseObject(response.getBody());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                log.warn("AI分析返回空choices，文章: {}", article.getTitle());
                return;
            }

            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String aiContent = message.getString("content");

            // 解析AI返回的JSON
            parseAiResult(article, aiContent);

            // 更新数据库
            newsArticleService.updateArticle(article);
            log.info("AI分析完成，文章 [{}] 情感: {}, 关键词: {}",
                    article.getTitle(), article.getSentiment(), article.getKeywords());
        }
        catch (Exception e)
        {
            log.error("AI分析异常，文章: {}", article.getTitle(), e);
        }
    }

    /**
     * 构建DeepSeek API请求体
     */
    private JSONObject buildRequestBody(NewsArticle article)
    {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", properties.getModel());
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 500);
        requestBody.put("stream", false);

        JSONArray messages = new JSONArray();

        // 系统提示
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", SYSTEM_PROMPT);
        messages.add(sysMsg);

        // 用户消息：标题 + 内容
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", buildUserContent(article));
        messages.add(userMsg);

        requestBody.put("messages", messages);
        return requestBody;
    }

    /**
     * 构建发送给AI的用户消息内容
     */
    private String buildUserContent(NewsArticle article)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(article.getTitle()).append("\n");

        String content = article.getContent();
        if (StringUtils.isEmpty(content))
        {
            content = article.getSummary();
        }
        if (StringUtils.isNotEmpty(content))
        {
            // 截断到2000字符，控制token消耗
            if (content.length() > 2000)
            {
                content = content.substring(0, 2000) + "...";
            }
            sb.append("内容：").append(content);
        }
        else
        {
            sb.append("内容：(无内容)");
        }

        return sb.toString();
    }

    /**
     * 解析AI返回的JSON结果，填充到文章实体中
     */
    private void parseAiResult(NewsArticle article, String aiContent)
    {
        String jsonStr = aiContent.trim();

        // 剥离可能的markdown代码块标记
        if (jsonStr.startsWith("```"))
        {
            jsonStr = jsonStr.replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();
        }

        try
        {
            JSONObject result = JSON.parseObject(jsonStr);
            article.setSummary(result.getString("summary"));
            article.setSentiment(result.getString("sentiment"));

            // tags - 可能是JSONArray或已经是字符串
            Object tagsObj = result.get("tags");
            if (tagsObj instanceof JSONArray)
            {
                article.setTags(((JSONArray) tagsObj).toJSONString());
            }
            else if (tagsObj instanceof String)
            {
                article.setTags((String) tagsObj);
            }

            article.setKeywords(result.getString("keywords"));
        }
        catch (Exception e)
        {
            log.warn("解析AI返回结果失败，原始内容: {}", aiContent, e);
        }
    }
}
