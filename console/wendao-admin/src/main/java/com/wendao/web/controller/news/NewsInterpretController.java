package com.wendao.web.controller.news;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wendao.common.annotation.Log;
import com.wendao.common.core.controller.BaseController;
import com.wendao.common.core.domain.AjaxResult;
import com.wendao.common.core.domain.model.LoginUser;
import com.wendao.common.enums.BusinessType;
import com.wendao.common.utils.StringUtils;
import com.wendao.framework.web.service.TokenService;
import com.wendao.system.cache.ModelConfigCache;
import com.wendao.system.config.NewsAiProperties;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.domain.NewsInterpretation;
import com.wendao.system.domain.NewsModelConfig;
import com.wendao.system.domain.NewsPromptConfig;
import com.wendao.system.service.INewsArticleService;
import com.wendao.system.service.INewsInterpretationService;
import com.wendao.system.service.INewsPromptConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 新闻一键解读 SSE 接口
 *
 * @author wendao
 */
@RestController
@RequestMapping("/news/article")
public class NewsInterpretController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(NewsInterpretController.class);

    /** 解读使用的提示词类型 */
    private static final String PROMPT_TYPE_INTERPRET = "INTERPRET";

    /** 兜底解读提示词（数据库未配置时使用） */
    private static final String FALLBACK_INTERPRET_PROMPT =
            "你是一位专业的新闻分析师，请对以下新闻进行深度解读。\n" +
            "【重要】直接用自然语言输出解读内容，禁止返回JSON格式。\n" +
            "输出要求：\n" +
            "1. 使用Markdown格式，分段清晰\n" +
            "2. 包含以下部分：## 背景分析、## 核心观点、## 影响评估、## 延伸思考\n" +
            "3. 如涉及复杂流程或关系，可用mermaid代码块绘制流程图\n" +
            "4. 语言专业、客观、深入，避免流水账式复述新闻内容";

    @Autowired
    private INewsArticleService newsArticleService;

    @Autowired
    private INewsInterpretationService interpretationService;

    @Autowired
    private INewsPromptConfigService promptConfigService;

    @Autowired
    private NewsAiProperties aiProperties;

    @Autowired
    private ModelConfigCache modelConfigCache;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private com.wendao.system.utils.AiApiClient aiApiClient;

    /**
     * 查询文章最新解读记录
     */
    @PreAuthorize("@ss.hasPermi('news:article:query')")
    @GetMapping("/interpret/{articleId}/latest")
    public AjaxResult getLatestInterpretation(@PathVariable Long articleId)
    {
        NewsInterpretation record = interpretationService.selectLatestByArticleId(articleId);
        return success(record);
    }

    /**
     * 查询文章所有历史解读记录（按时间倒序）
     */
    @PreAuthorize("@ss.hasPermi('news:article:query')")
    @GetMapping("/interpret/{articleId}/list")
    public AjaxResult getInterpretationList(@PathVariable Long articleId)
    {
        java.util.List<NewsInterpretation> list = interpretationService.selectListByArticleId(articleId);
        return success(list);
    }

    /**
     * 一键解读 SSE 接口（StreamingResponseBody 真流式）
     * EventSource / fetch 均可使用，通过 query param 传 token 验证身份
     */
    @Log(title = "新闻解读", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/interpret/{articleId}")
    public ResponseEntity<StreamingResponseBody> interpret(
            @PathVariable Long articleId,
            @RequestParam(required = false) String token)
    {
        // 从 query param 或 SecurityContext 获取登录用户
        LoginUser loginUser = resolveLoginUser(token);
        final String username = loginUser != null ? loginUser.getUsername() : "anonymous";

        StreamingResponseBody streamingResponseBody = outputStream -> {
            doInterpret(articleId, username, outputStream);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(streamingResponseBody);
    }

    // -----------------------------------------------------------------------
    // 核心解读逻辑
    // -----------------------------------------------------------------------

    private void doInterpret(Long articleId, String username, OutputStream outputStream)
    {
        NewsInterpretation record = null;
        StringBuilder fullContent = new StringBuilder();
        HttpURLConnection connection = null;

        try
        {
            // 1. 校验文章存在
            NewsArticle article = newsArticleService.selectArticleById(articleId);
            if (article == null)
            {
                writeError(outputStream, "文章不存在，ID=" + articleId);
                return;
            }

            // 2. 从缓存获取模型配置（按优先级选第一个 INTERPRET 类型）
            NewsModelConfig modelConfig = modelConfigCache.getModelConfig(PROMPT_TYPE_INTERPRET);
            if (modelConfig == null || StringUtils.isEmpty(modelConfig.getApiKey()))
            {
                writeError(outputStream, "无可用AI模型配置，请在模型管理中配置 INTERPRET 类型的模型");
                return;
            }

            // 3. 如果上一条记录仍在进行中，标记为已取消
            NewsInterpretation latest = interpretationService.selectLatestByArticleId(articleId);
            if (latest != null && "0".equals(latest.getStatus()))
            {
                markFailed(latest, "用户重新发起解读，本次已取消");
            }

            // 4. 查找匹配的提示词配置
            NewsPromptConfig promptCfg = findPromptConfig(article);
            String systemPrompt = promptCfg != null && StringUtils.isNotEmpty(promptCfg.getSystemPrompt())
                    ? promptCfg.getSystemPrompt()
                    : FALLBACK_INTERPRET_PROMPT;

            // 5. 创建解读记录（status=0 进行中）
            record = new NewsInterpretation();
            record.setArticleId(articleId);
            record.setPromptConfigId(promptCfg != null ? promptCfg.getId() : null);
            record.setPromptSnapshot(systemPrompt);
            record.setStatus("0");
            record.setModelName(modelConfig.getModelName());
            record.setInterpretCount(latest != null ? latest.getInterpretCount() + 1 : 1);
            record.setCreateBy(username);
            interpretationService.insert(record);

            // 6. 通知前端：解读开始
            JSONObject startData = new JSONObject();
            startData.put("recordId", record.getId());
            startData.put("interpretCount", record.getInterpretCount());
            startData.put("modelName", modelConfig.getModelName());
            writeEvent(outputStream, "start", startData.toJSONString());

            // 7. 构建消息列表和参数
            java.util.List<java.util.Map<String, String>> messages = buildMessages(article, systemPrompt);
            double temperature = resolveTemperature(promptCfg, modelConfig);
            int maxTokens = resolveMaxTokens(promptCfg, modelConfig);

            // 8. 通过 AiApiClient 建立流式连接
            connection = aiApiClient.callStreaming(modelConfig, messages, maxTokens, temperature);

            // 9. 检查 HTTP 状态
            int httpCode = connection.getResponseCode();
            if (httpCode != 200)
            {
                String errBody = "";
                try (BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8)))
                {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = errReader.readLine()) != null) sb.append(l);
                    errBody = sb.toString();
                }
                log.error("DeepSeek API 返回错误，状态码={}，body={}", httpCode, errBody);
                markFailed(record, "AI服务返回异常状态码：" + httpCode);
                writeError(outputStream, "AI服务异常，状态码：" + httpCode);
                return;
            }

            // 9. 逐行读取流式响应，实时转发给前端
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                boolean isAnthropic = "ANTHROPIC".equalsIgnoreCase(modelConfig.getApiFormat());
                while ((line = reader.readLine()) != null)
                {
                    if (line.isEmpty()) continue;

                    // 检查流结束标记
                    if (aiApiClient.isStreamDone(line, modelConfig.getApiFormat()))
                    {
                        break;
                    }

                    // 使用 AiApiClient 统一解析增量内容
                    String deltaContent = aiApiClient.parseStreamLine(line, modelConfig.getApiFormat());

                    // 累积完整内容用于落库
                    if (StringUtils.isNotEmpty(deltaContent))
                    {
                        fullContent.append(deltaContent);
                    }

                    if (isAnthropic)
                    {
                        // Anthropic 格式：将内容块转换为 OpenAI 兼容的 SSE 格式转发给前端
                        if (StringUtils.isNotEmpty(deltaContent))
                        {
                            JSONObject fakeOpenAI = new JSONObject();
                            JSONArray choices = new JSONArray();
                            JSONObject choice = new JSONObject();
                            JSONObject delta = new JSONObject();
                            delta.put("content", deltaContent);
                            choice.put("delta", delta);
                            choice.put("index", 0);
                            choices.add(choice);
                            fakeOpenAI.put("choices", choices);
                            String eventData = "data: " + fakeOpenAI.toJSONString() + "\n\n";
                            outputStream.write(eventData.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                        // 非内容事件（ping, message_start, content_block_start 等）跳过，不转发
                    }
                    else
                    {
                        // OpenAI 格式：直接将原始 data: 行转发给前端
                        String eventData = line + "\n\n";
                        outputStream.write(eventData.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                }
            }

            // 10. 落库：保存完整内容
            record.setContent(fullContent.toString());
            record.setStatus("1");
            record.setUpdateTime(new Date());
            interpretationService.update(record);

            // 11. 通知前端完成（自定义 done 事件）
            JSONObject doneData = new JSONObject();
            doneData.put("modelName", modelConfig.getModelName());
            writeEvent(outputStream, "done", doneData.toJSONString());
            outputStream.flush();
        }
        catch (Exception e)
        {
            log.error("解读异常，文章ID={}", articleId, e);
            if (record != null && record.getId() != null)
            {
                markFailed(record, e.getMessage());
            }
            try
            {
                writeError(outputStream, "解读过程发生异常：" + e.getMessage());
            }
            catch (Exception ignored) {}
        }
        finally
        {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * 构建消息列表（system + user）
     */
    private java.util.List<java.util.Map<String, String>> buildMessages(NewsArticle article, String systemPrompt)
    {
        java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();

        java.util.Map<String, String> sysMsg = new java.util.HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        java.util.Map<String, String> userMsg = new java.util.HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", buildUserContent(article));
        messages.add(userMsg);

        return messages;
    }

    /**
     * 解析温度参数
     */
    private double resolveTemperature(NewsPromptConfig promptCfg, NewsModelConfig modelConfig)
    {
        if (promptCfg != null && promptCfg.getTemperature() != null) return promptCfg.getTemperature();
        if (modelConfig.getTemperature() != null) return modelConfig.getTemperature().doubleValue();
        return 0.5;
    }

    /**
     * 解析最大 token 数
     */
    private int resolveMaxTokens(NewsPromptConfig promptCfg, NewsModelConfig modelConfig)
    {
        if (promptCfg != null && promptCfg.getMaxTokens() != null) return promptCfg.getMaxTokens();
        if (modelConfig.getMaxTokens() != null) return modelConfig.getMaxTokens();
        return 2000;
    }

    /**
     * 组装用户消息内容
     */
    private String buildUserContent(NewsArticle article)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("【标题】").append(article.getTitle()).append("\n");
        if (StringUtils.isNotEmpty(article.getSourceName()))
        {
            sb.append("【来源】").append(article.getSourceName()).append("\n");
        }
        if (article.getPublishTime() != null)
        {
            sb.append("【发布时间】").append(article.getPublishTime()).append("\n");
        }
        if (StringUtils.isNotEmpty(article.getTypeName()))
        {
            sb.append("【新闻类型】").append(article.getTypeName()).append("\n");
        }
        if (StringUtils.isNotEmpty(article.getKeywords()))
        {
            sb.append("【关键词】").append(article.getKeywords()).append("\n");
        }
        sb.append("\n【正文】\n");
        String content = article.getContent();
        if (StringUtils.isEmpty(content)) content = article.getSummary();
        if (StringUtils.isNotEmpty(content))
        {
            if (content.length() > 3000) content = content.substring(0, 3000) + "...（内容已截断）";
            sb.append(content);
        }
        else
        {
            sb.append("（暂无正文，请基于标题进行解读）");
        }
        return sb.toString();
    }

    /**
     * 查找匹配的提示词配置
     */
    private NewsPromptConfig findPromptConfig(NewsArticle article)
    {
        if (article.getTypeConfigId() != null)
        {
            NewsPromptConfig cfg = promptConfigService.selectMatch(article.getTypeConfigId(), PROMPT_TYPE_INTERPRET);
            if (cfg != null) return cfg;
        }
        return promptConfigService.selectMatch(null, PROMPT_TYPE_INTERPRET);
    }

    /**
     * 将记录标记为失败并更新数据库
     */
    private void markFailed(NewsInterpretation record, String errMsg)
    {
        record.setStatus("2");
        record.setErrorMsg(StringUtils.substring(errMsg, 0, 500));
        record.setUpdateTime(new Date());
        try
        {
            interpretationService.update(record);
        }
        catch (Exception e)
        {
            log.error("更新解读失败状态异常", e);
        }
    }

    /**
     * 写出自定义 SSE 事件（非 DeepSeek 原始数据行）
     */
    private void writeEvent(OutputStream out, String eventName, String data) throws Exception
    {
        String text = "event: " + eventName + "\ndata: " + data + "\n\n";
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * 写出错误事件
     */
    private void writeError(OutputStream out, String message) throws Exception
    {
        writeEvent(out, "error", message);
    }

    /**
     * 解析登录用户（支持 query param token 和 SecurityContext 两种方式）
     */
    private LoginUser resolveLoginUser(String queryToken)
    {
        if (StringUtils.isNotEmpty(queryToken))
        {
            return tokenService.getLoginUserByToken(queryToken);
        }
        try
        {
            return (LoginUser) org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
