package com.wendao.system.service.impl;

import java.util.List;
import java.util.stream.Collectors;
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
import com.wendao.common.utils.StringUtils;
import com.wendao.system.config.NewsAiProperties;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.domain.NewsPromptConfig;
import com.wendao.system.domain.NewsTypeConfig;
import com.wendao.system.service.INewsAiAnalysisService;
import com.wendao.system.service.INewsArticleService;
import com.wendao.system.service.INewsPromptConfigService;
import com.wendao.system.service.INewsTypeConfigService;

/**
 * AI新闻分析 服务层实现（提示词从DB读取，AI自动分类）
 *
 * @author wendao
 */
@Service
public class NewsAiAnalysisServiceImpl implements INewsAiAnalysisService
{
    private static final Logger log = LoggerFactory.getLogger(NewsAiAnalysisServiceImpl.class);

    @Autowired
    private NewsAiProperties properties;

    @Autowired
    private INewsArticleService newsArticleService;

    @Autowired
    private INewsPromptConfigService promptConfigService;

    @Autowired
    private INewsTypeConfigService typeConfigService;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 兜底基础提示词（DB未配置时使用） */
    private static final String FALLBACK_BASIC_PROMPT =
        "你是一个专业的新闻分析助手。分析新闻并返回JSON：\n" +
        "{\"summary\":\"摘要\",\"tags\":[\"标签\"],\"sentiment\":\"positive|negative|neutral\",\"keywords\":\"关键词\"}";

    /** 兜底深度提示词 */
    private static final String FALLBACK_DEEP_PROMPT =
        "你是一个热点内容分析专家。深度分析新闻并返回JSON：\n" +
        "{\"isReal\":true/false,\"relevance\":0-100,\"relevanceReason\":\"理由\",\"keywordMentioned\":true/false,\"importance\":\"low|medium|high|urgent\",\"summary\":\"摘要\",\"tags\":[],\"sentiment\":\"positive|negative|neutral\",\"keywords\":\"关键词\"}";

    @Async("aiAnalysisExecutor")
    @Override
    public void analyzeAsync(NewsArticle article)
    {
        doAnalyze(article, null, "ANALYSIS");
    }

    @Async("aiAnalysisExecutor")
    @Override
    public void analyzeDeepAsync(NewsArticle article, String keyword)
    {
        doAnalyze(article, keyword, "ANALYSIS");
    }

    private void doAnalyze(NewsArticle article, String keyword, String promptType)
    {
        if (!properties.isEnabled() || StringUtils.isEmpty(properties.getApiKey()))
        {
            log.debug("AI分析未启用或未配置API Key，跳过文章 [{}]", article.getTitle());
            return;
        }

        // 检查数据库中是否有启用的新闻类型，没有则不进行分析
        List<NewsTypeConfig> activeTypes = typeConfigService.selectActive();
        if (activeTypes.isEmpty())
        {
            log.debug("无启用的新闻类型配置，跳过AI分析 [{}]", article.getTitle());
            return;
        }

        try
        {
            // 从DB查找匹配的提示词配置
            NewsPromptConfig promptCfg = findPromptConfig(article, promptType);

            JSONObject requestBody = buildRequestBody(article, keyword, promptCfg, activeTypes);

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

            JSONObject respJson = JSON.parseObject(response.getBody());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                log.warn("AI分析返回空choices，文章: {}", article.getTitle());
                return;
            }

            String aiContent = choices.getJSONObject(0).getJSONObject("message").getString("content");
            boolean isDeep = "ANALYSIS".equals(promptType) && keyword != null;
            parseAiResult(article, aiContent, isDeep);

            newsArticleService.updateArticle(article);
            log.info("AI分析完成，文章 [{}] typeConfigId={} sentiment={}",
                    article.getTitle(), article.getTypeConfigId(), article.getSentiment());
        }
        catch (Exception e)
        {
            log.error("AI分析异常，文章: {}", article.getTitle(), e);
        }
    }

    /**
     * 查找匹配的提示词配置
     * 优先级：文章typeConfigId+promptType > promptType（不限类型） > 兜底
     */
    private NewsPromptConfig findPromptConfig(NewsArticle article, String promptType)
    {
        // 1. 精确匹配：文章类型 + promptType
        if (article.getTypeConfigId() != null)
        {
            NewsPromptConfig cfg = promptConfigService.selectMatch(article.getTypeConfigId(), promptType);
            if (cfg != null)
            {
                log.debug("精确匹配提示词 typeConfigId={} promptType={}", article.getTypeConfigId(), promptType);
                return cfg;
            }
        }
        // 2. 降级：只按 promptType 查找
        NewsPromptConfig cfg = promptConfigService.selectMatch(null, promptType);
        if (cfg != null)
        {
            log.debug("降级匹配提示词 promptType={}", promptType);
        }
        return cfg;
    }

    /**
     * 构建DeepSeek API请求体
     */
    private JSONObject buildRequestBody(NewsArticle article, String keyword, NewsPromptConfig promptCfg, List<NewsTypeConfig> activeTypes)
    {
        JSONObject body = new JSONObject();

        // 模型统一使用后端配置
        String model = properties.getModel();
        double temperature = 0.3;
        int maxTokens = 500;
        String systemPrompt = keyword != null ? FALLBACK_DEEP_PROMPT : FALLBACK_BASIC_PROMPT;

        if (promptCfg != null)
        {
            if (promptCfg.getTemperature() != null) temperature = promptCfg.getTemperature();
            if (promptCfg.getMaxTokens() != null) maxTokens = promptCfg.getMaxTokens();
            if (StringUtils.isNotEmpty(promptCfg.getSystemPrompt())) systemPrompt = promptCfg.getSystemPrompt();
        }

        // 把可用新闻类型列表注入 system_prompt，让AI自动分类
        String typeHint = buildTypeHint(activeTypes);
        if (StringUtils.isNotEmpty(typeHint))
        {
            systemPrompt = systemPrompt + "\n\n" + typeHint;
        }

        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        String userContent = buildUserContent(article);
        if (StringUtils.isNotEmpty(keyword))
        {
            userContent = "【监控关键词：" + keyword + "】\n" + userContent;
        }
        userMsg.put("content", userContent);
        messages.add(userMsg);

        body.put("messages", messages);
        return body;
    }

    /**
     * 构建可用新闻类型提示（让AI返回typeCode字段实现自动分类）
     */
    private String buildTypeHint(List<NewsTypeConfig> types)
    {
        if (types.isEmpty()) return "";

        String typeList = types.stream()
                .map(t -> t.getTypeCode() + ":" + t.getTypeName())
                .collect(Collectors.joining(", "));

        return "【新闻类型分类】请根据文章内容判断它属于以下哪个类型，并在返回的JSON中增加 \"typeCode\" 字段（从下列code中选择最匹配的一个）。"
                + "如果无法判断，请选择最接近的类型。\n"
                + "可用类型：" + typeList + "\n"
                + "示例返回：{\"typeCode\":\"ai_tech\",\"summary\":\"...\",\"tags\":[...],\"sentiment\":\"neutral\",\"keywords\":\"...\"}";
    }

    private String buildUserContent(NewsArticle article)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(article.getTitle()).append("\n");

        String content = article.getContent();
        if (StringUtils.isEmpty(content)) content = article.getSummary();
        if (StringUtils.isNotEmpty(content))
        {
            if (content.length() > 2000) content = content.substring(0, 2000) + "...";
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
    private void parseAiResult(NewsArticle article, String aiContent, boolean deep)
    {
        String jsonStr = aiContent.trim();
        if (jsonStr.startsWith("```"))
        {
            jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        try
        {
            JSONObject result = JSON.parseObject(jsonStr);

            // === 基础字段 ===
            article.setSummary(result.getString("summary"));
            article.setSentiment(result.getString("sentiment"));

            Object tagsObj = result.get("tags");
            if (tagsObj instanceof JSONArray) article.setTags(((JSONArray) tagsObj).toJSONString());
            else if (tagsObj instanceof String) article.setTags((String) tagsObj);

            article.setKeywords(result.getString("keywords"));

            // === AI自动分类：typeCode → typeConfigId ===
            String typeCode = result.getString("typeCode");
            if (StringUtils.isNotEmpty(typeCode))
            {
                NewsTypeConfig typeConfig = typeConfigService.selectByCode(typeCode.trim());
                if (typeConfig != null)
                {
                    article.setTypeConfigId(typeConfig.getId());
                    log.debug("AI自动分类：typeCode={} → typeConfigId={} ({})",
                            typeCode, typeConfig.getId(), typeConfig.getTypeName());
                }
                else
                {
                    log.debug("AI返回的typeCode [{}] 未匹配到任何类型配置", typeCode);
                }
            }

            // === 深度分析字段 ===
            if (deep)
            {
                if (result.containsKey("isReal"))
                    article.setIsReal(Boolean.TRUE.equals(result.getBoolean("isReal")) ? 1 : 0);
                if (result.containsKey("relevance"))
                    article.setRelevance(Math.min(100, Math.max(0, result.getIntValue("relevance"))));
                article.setRelevanceReason(result.getString("relevanceReason"));
                if (result.containsKey("keywordMentioned"))
                    article.setKeywordMentioned(Boolean.TRUE.equals(result.getBoolean("keywordMentioned")) ? 1 : 0);
                String imp = result.getString("importance");
                if (imp != null && imp.matches("low|medium|high|urgent")) article.setImportance(imp);
            }
        }
        catch (Exception e)
        {
            log.warn("解析AI返回结果失败，原始内容: {}", aiContent, e);
        }
    }
}
