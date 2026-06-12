package com.wendao.system.service.impl;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.cache.ModelConfigCache;
import com.wendao.system.config.NewsAiProperties;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.domain.NewsModelConfig;
import com.wendao.system.domain.NewsPromptConfig;
import com.wendao.system.domain.NewsTypeConfig;
import com.wendao.system.service.INewsAiAnalysisService;
import com.wendao.system.service.INewsArticleService;
import com.wendao.system.service.INewsPromptConfigService;
import com.wendao.system.service.INewsTypeConfigService;

/**
 * AI新闻分析 服务层实现（模型从DB模型配置表按优先级选取，API Key AES加解密）
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
    private ModelConfigCache modelConfigCache;

    @Autowired
    private INewsArticleService newsArticleService;

    @Autowired
    private INewsPromptConfigService promptConfigService;

    @Autowired
    private INewsTypeConfigService typeConfigService;

    @Autowired
    private com.wendao.system.utils.AiApiClient aiApiClient;

    /** AI分析场景标识 */
    private static final String USAGE_TYPE = "ANALYSIS";

    /** 兜底基础提示词（DB未配置时使用） */
    private static final String FALLBACK_BASIC_PROMPT =
        "你是一个专业的新闻分析助手。分析新闻并严格返回JSON，不要包含任何额外文字、解释或markdown格式：\n" +
        "{\"summary\":\"摘要\",\"tags\":[\"标签\"],\"sentiment\":\"positive|negative|neutral\",\"keywords\":\"关键词\"}";

    /** 兜底深度提示词 */
    private static final String FALLBACK_DEEP_PROMPT =
        "你是一个热点内容分析专家。深度分析新闻并严格返回JSON，不要包含任何额外文字、解释或markdown格式：\n" +
        "{\"isReal\":true/false,\"relevance\":0-100,\"relevanceReason\":\"理由\",\"keywordMentioned\":true/false,\"importance\":\"low|medium|high|urgent\",\"summary\":\"摘要\",\"tags\":[],\"sentiment\":\"positive|negative|neutral\",\"keywords\":\"关键词\"}";

    @Async("aiAnalysisExecutor")
    @Override
    public void analyzeAsync(NewsArticle article)
    {
        doAnalyze(article, null, 0);
    }

    @Async("aiAnalysisExecutor")
    @Override
    public void analyzeDeepAsync(NewsArticle article, String keyword)
    {
        doAnalyze(article, keyword, 0);
    }

    @Async("aiAnalysisExecutor")
    @Override
    public void analyzeDeepAsync(NewsArticle article, String keyword, int relevanceThreshold)
    {
        doAnalyze(article, keyword, relevanceThreshold);
    }

    private void doAnalyze(NewsArticle article, String keyword, int relevanceThreshold)
    {
        // 主开关
        if (!properties.isEnabled())
        {
            log.debug("AI分析未启用，跳过文章 [{}]", article.getTitle());
            return;
        }

        // 从缓存获取模型配置（按优先级选第一个）
        NewsModelConfig modelConfig = modelConfigCache.getModelConfig(USAGE_TYPE);
        if (modelConfig == null || StringUtils.isEmpty(modelConfig.getApiKey()))
        {
            log.debug("无可用AI模型配置，跳过AI分析 [{}]", article.getTitle());
            return;
        }

        // 检查数据库中是否有启用的新闻类型
        List<NewsTypeConfig> activeTypes = typeConfigService.selectActive();
        if (activeTypes.isEmpty())
        {
            log.debug("无启用的新闻类型配置，跳过AI分析 [{}]", article.getTitle());
            return;
        }

        try
        {
            // 从DB查找匹配的提示词配置
            NewsPromptConfig promptCfg = findPromptConfig(article, USAGE_TYPE);

            JSONObject requestBody = buildRequestBody(article, keyword, promptCfg, activeTypes, relevanceThreshold, modelConfig);

            // 提取 messages 列表和参数
            boolean useDeep = keyword != null || relevanceThreshold > 0;
            int maxTokens = useDeep ? 400 : 300;
            double temperature = 0.3;
            if (promptCfg != null)
            {
                if (promptCfg.getTemperature() != null) temperature = promptCfg.getTemperature();
                if (promptCfg.getMaxTokens() != null) maxTokens = promptCfg.getMaxTokens();
            }
            if (modelConfig.getMaxTokens() != null && promptCfg == null) maxTokens = modelConfig.getMaxTokens();
            if (modelConfig.getTemperature() != null && promptCfg == null) temperature = modelConfig.getTemperature().doubleValue();

            java.util.List<java.util.Map<String, String>> messages = extractMessages(requestBody);
            boolean jsonMode = modelConfig.getSupportJsonMode() != null && modelConfig.getSupportJsonMode() == 1;

            String aiContent = aiApiClient.callNonStreaming(modelConfig, messages, maxTokens, temperature, jsonMode);

            parseAiResult(article, aiContent, useDeep, relevanceThreshold);

            newsArticleService.updateArticle(article);
            log.info("AI分析完成，模型: {}，文章 [{}] typeConfigId={} sentiment={}",
                    modelConfig.getModelName(), article.getTitle(), article.getTypeConfigId(), article.getSentiment());
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
        if (article.getTypeConfigId() != null)
        {
            NewsPromptConfig cfg = promptConfigService.selectMatch(article.getTypeConfigId(), promptType);
            if (cfg != null)
            {
                return cfg;
            }
        }
        return promptConfigService.selectMatch(null, promptType);
    }

    /**
     * 构建AI API请求体
     */
    private JSONObject buildRequestBody(NewsArticle article, String keyword, NewsPromptConfig promptCfg,
                                         List<NewsTypeConfig> activeTypes, int relevanceThreshold,
                                         NewsModelConfig modelConfig)
    {
        JSONObject body = new JSONObject();

        boolean useDeep = keyword != null || relevanceThreshold > 0;
        int maxTokens = useDeep ? 400 : 300;
        double temperature = 0.3;
        String systemPrompt = useDeep ? FALLBACK_DEEP_PROMPT : FALLBACK_BASIC_PROMPT;

        // 提示词配置覆盖默认值
        if (promptCfg != null)
        {
            if (promptCfg.getTemperature() != null) temperature = promptCfg.getTemperature();
            if (promptCfg.getMaxTokens() != null) maxTokens = promptCfg.getMaxTokens();
            if (StringUtils.isNotEmpty(promptCfg.getSystemPrompt())) systemPrompt = promptCfg.getSystemPrompt();
        }

        // 模型配置覆盖 maxTokens / temperature（优先用模型默认值）
        if (modelConfig.getMaxTokens() != null && promptCfg == null) maxTokens = modelConfig.getMaxTokens();
        if (modelConfig.getTemperature() != null && promptCfg == null)
            temperature = modelConfig.getTemperature().doubleValue();

        // 注入可用新闻类型列表
        String typeHint = buildTypeHint(activeTypes);
        if (StringUtils.isNotEmpty(typeHint))
        {
            systemPrompt = systemPrompt + "\n\n" + typeHint;
        }

        body.put("model", modelConfig.getModelName());
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);

        // JSON 结构化输出（如果模型支持）
        if (modelConfig.getSupportJsonMode() != null && modelConfig.getSupportJsonMode() == 1)
        {
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", "json_object");
            body.put("response_format", responseFormat);
        }

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
     * 从 buildRequestBody 构建的 JSONObject 中提取 messages 列表
     */
    private java.util.List<java.util.Map<String, String>> extractMessages(JSONObject requestBody)
    {
        java.util.List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        JSONArray messages = requestBody.getJSONArray("messages");
        if (messages != null)
        {
            for (int i = 0; i < messages.size(); i++)
            {
                JSONObject msg = messages.getJSONObject(i);
                java.util.Map<String, String> map = new java.util.HashMap<>();
                map.put("role", msg.getString("role"));
                map.put("content", msg.getString("content"));
                result.add(map);
            }
        }
        return result;
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
                + "重要：只返回JSON对象，不要包含任何额外文字、解释或markdown格式。\n"
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
    private void parseAiResult(NewsArticle article, String aiContent, boolean deep, int relevanceThreshold)
    {
        String jsonStr = extractJson(aiContent);
        if (jsonStr == null)
        {
            log.warn("无法从AI返回内容中提取JSON，文章: {}，原始内容前200字: {}",
                    article.getTitle(), aiContent.substring(0, Math.min(aiContent.length(), 200)));
            return;
        }
        try
        {
            JSONObject result = JSON.parseObject(jsonStr);

            article.setSummary(result.getString("summary"));
            article.setSentiment(result.getString("sentiment"));

            Object tagsObj = result.get("tags");
            if (tagsObj instanceof JSONArray) article.setTags(((JSONArray) tagsObj).toJSONString());
            else if (tagsObj instanceof String) article.setTags((String) tagsObj);

            article.setKeywords(result.getString("keywords"));

            // AI自动分类
            String typeCode = result.getString("typeCode");
            if (StringUtils.isNotEmpty(typeCode))
            {
                NewsTypeConfig typeConfig = typeConfigService.selectByCode(typeCode.trim());
                if (typeConfig != null)
                {
                    article.setTypeConfigId(typeConfig.getId());
                }
            }

            // 深度分析字段
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

                if (relevanceThreshold > 0 && article.getRelevance() != null
                        && article.getRelevance() < relevanceThreshold)
                {
                    article.setStatus("1");
                }
            }
        }
        catch (Exception e)
        {
            log.warn("解析AI返回结果失败，原始内容: {}", aiContent, e);
        }
    }

    /**
     * 从AI返回内容中提取JSON字符串
     */
    private String extractJson(String content)
    {
        if (StringUtils.isEmpty(content)) return null;
        String trimmed = content.trim();

        if (trimmed.startsWith("{"))
        {
            try { JSON.parseObject(trimmed); return trimmed; }
            catch (Exception ignored) {}
        }

        if (trimmed.contains("```"))
        {
            String stripped = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            if (stripped.startsWith("{"))
            {
                try { JSON.parseObject(stripped); return stripped; }
                catch (Exception ignored) {}
            }
        }

        int start = trimmed.indexOf('{');
        if (start >= 0)
        {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < trimmed.length(); i++)
            {
                char c = trimmed.charAt(i);
                if (escaped) { escaped = false; continue; }
                if (c == '\\' && inString) { escaped = true; continue; }
                if (c == '"') { inString = !inString; continue; }
                if (!inString)
                {
                    if (c == '{') depth++;
                    else if (c == '}')
                    {
                        depth--;
                        if (depth == 0)
                        {
                            String candidate = trimmed.substring(start, i + 1);
                            try { JSON.parseObject(candidate); return candidate; }
                            catch (Exception ignored) { break; }
                        }
                    }
                }
            }
        }

        return null;
    }

    // ===================================================================
    // 批量相关性评分（入库前同步调用，1次AI评N篇）
    // ===================================================================

    private static final String BATCH_SCORE_PROMPT =
        "你是一个新闻相关性评估专家。请评估以下新闻标题与AI/科技领域的相关性。\n" +
        "对每条新闻，返回 relevance(0-100，越高越相关，不相关给低分)、isReal(true/false，是否真实新闻)、importance(low/medium/high/urgent)、reason(简要理由)。\n" +
        "严格返回JSON数组格式，不要包含任何额外文字、解释或markdown格式：\n" +
        "[{\"index\":0,\"relevance\":85,\"isReal\":true,\"importance\":\"high\",\"reason\":\"...\"}, ...]\n" +
        "index必须与输入的编号对应。";

    @Override
    public List<INewsAiAnalysisService.RelevanceScore> batchScoreRelevance(List<NewsArticle> articles)
    {
        if (articles == null || articles.isEmpty()) return java.util.Collections.emptyList();

        // AI 未启用时默认全部放行（60分）
        if (!properties.isEnabled())
        {
            log.debug("AI分析未启用，批量评分默认放行 {} 篇", articles.size());
            return defaultPassAll(articles);
        }

        // 获取模型配置
        NewsModelConfig modelConfig = modelConfigCache.getModelConfig("ANALYSIS");
        if (modelConfig == null || StringUtils.isEmpty(modelConfig.getApiKey()))
        {
            log.debug("无可用AI模型配置，批量评分默认放行 {} 篇", articles.size());
            return defaultPassAll(articles);
        }

        try
        {
            // 构建批量评分 prompt
            String prompt = buildBatchScorePrompt(articles);

            // 构建 messages
            int maxTokens = 200 + articles.size() * 80;
            double temperature = 0.3;
            boolean jsonMode = modelConfig.getSupportJsonMode() != null && modelConfig.getSupportJsonMode() == 1;

            java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
            java.util.Map<String, String> sysMsg = new java.util.HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", prompt);
            messages.add(sysMsg);

            java.util.Map<String, String> userMsg = new java.util.HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", buildBatchScoreUserContent(articles));
            messages.add(userMsg);

            String response = aiApiClient.callNonStreaming(modelConfig, messages, maxTokens, temperature, jsonMode);

            return parseBatchScoreResult(response, articles.size());
        }
        catch (Exception e)
        {
            log.error("批量AI评分失败，丢弃 {} 篇文章", articles.size(), e);
            return java.util.Collections.emptyList();
        }
    }

    private String buildBatchScorePrompt(List<NewsArticle> articles)
    {
        return BATCH_SCORE_PROMPT;
    }

    private String buildBatchScoreUserContent(List<NewsArticle> articles)
    {
        StringBuilder sb = new StringBuilder("新闻列表：\n");
        for (int i = 0; i < articles.size(); i++)
        {
            sb.append("[").append(i).append("] ").append(articles.get(i).getTitle()).append("\n");
        }
        return sb.toString();
    }

    private List<INewsAiAnalysisService.RelevanceScore> parseBatchScoreResult(String aiContent, int expectedSize)
    {
        String jsonStr = extractJsonArray(aiContent);
        if (jsonStr == null)
        {
            log.warn("批量评分AI返回无法解析，原始内容前200字: {}",
                    aiContent != null ? aiContent.substring(0, Math.min(aiContent.length(), 200)) : "null");
            return java.util.Collections.emptyList();
        }
        try
        {
            com.alibaba.fastjson2.JSONArray arr = com.alibaba.fastjson2.JSON.parseArray(jsonStr);
            java.util.List<INewsAiAnalysisService.RelevanceScore> result = new java.util.ArrayList<>();
            for (int i = 0; i < arr.size(); i++)
            {
                com.alibaba.fastjson2.JSONObject item = arr.getJSONObject(i);
                INewsAiAnalysisService.RelevanceScore score = new INewsAiAnalysisService.RelevanceScore();
                score.setIndex(item.getIntValue("index"));
                score.setRelevance(Math.min(100, Math.max(0, item.getIntValue("relevance"))));
                score.setReal(Boolean.TRUE.equals(item.getBoolean("isReal")));
                score.setImportance(item.getString("importance"));
                score.setReason(item.getString("reason"));
                result.add(score);
            }
            // 按 index 排序确保与输入顺序一致
            result.sort(java.util.Comparator.comparingInt(INewsAiAnalysisService.RelevanceScore::getIndex));
            return result;
        }
        catch (Exception e)
        {
            log.warn("批量评分JSON解析失败: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 从 AI 返回内容中提取 JSON 数组字符串
     */
    private String extractJsonArray(String content)
    {
        if (StringUtils.isEmpty(content)) return null;
        String trimmed = content.trim();

        // 直接是 JSON 数组
        if (trimmed.startsWith("["))
        {
            try { com.alibaba.fastjson2.JSON.parseArray(trimmed); return trimmed; }
            catch (Exception ignored) {}
        }

        // markdown 代码块包裹
        if (trimmed.contains("```"))
        {
            String stripped = trimmed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            if (stripped.startsWith("["))
            {
                try { com.alibaba.fastjson2.JSON.parseArray(stripped); return stripped; }
                catch (Exception ignored) {}
            }
        }

        // 查找第一个 [ 到最后一个 ]
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start)
        {
            String candidate = trimmed.substring(start, end + 1);
            try { com.alibaba.fastjson2.JSON.parseArray(candidate); return candidate; }
            catch (Exception ignored) {}
        }

        return null;
    }

    private List<INewsAiAnalysisService.RelevanceScore> defaultPassAll(List<NewsArticle> articles)
    {
        java.util.List<INewsAiAnalysisService.RelevanceScore> result = new java.util.ArrayList<>();
        for (int i = 0; i < articles.size(); i++)
        {
            INewsAiAnalysisService.RelevanceScore s = new INewsAiAnalysisService.RelevanceScore();
            s.setIndex(i);
            s.setRelevance(60);
            s.setReal(true);
            s.setImportance("low");
            s.setReason("AI未启用，默认放行");
            result.add(s);
        }
        return result;
    }
}
