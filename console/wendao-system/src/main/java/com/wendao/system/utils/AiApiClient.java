package com.wendao.system.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wendao.system.domain.NewsModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI API 调用客户端 — 封装 OpenAI 与 Anthropic 两种格式的差异
 * <p>
 * 两种格式的核心差异：
 * <ul>
 *   <li>URL 路径：/v1/chat/completions vs /v1/messages</li>
 *   <li>认证头：Authorization: Bearer vs x-api-key + anthropic-version</li>
 *   <li>请求体：OpenAI 有 temperature/response_format，Anthropic 有 system 顶层字段</li>
 *   <li>响应解析：choices[0].message.content vs content[0].text</li>
 *   <li>流式事件：choices[0].delta.content vs content_block_delta.delta.text</li>
 * </ul>
 *
 * @author wendao
 */
@Component
public class AiApiClient
{
    private static final Logger log = LoggerFactory.getLogger(AiApiClient.class);

    private static final String FORMAT_ANTHROPIC = "ANTHROPIC";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    // ===================================================================
    // 非流式调用
    // ===================================================================

    /**
     * 非流式调用 AI API，返回提取的文本内容
     *
     * @param config      模型配置（含 apiFormat、apiUrl、apiKey、modelName、timeoutMs）
     * @param messages    消息列表 [{role, content}, ...]
     * @param maxTokens   最大输出 token 数
     * @param temperature 温度参数（可为 null）
     * @param jsonMode    是否启用 JSON 结构化输出（仅 OpenAI 有效）
     * @return AI 返回的文本内容
     * @throws IOException 网络或 API 错误
     */
    public String callNonStreaming(NewsModelConfig config, List<Map<String, String>> messages,
                                   Integer maxTokens, Double temperature, Boolean jsonMode)
            throws IOException
    {
        boolean isAnthropic = FORMAT_ANTHROPIC.equalsIgnoreCase(config.getApiFormat());

        // 1. 构建请求体
        JSONObject body = buildRequestBody(config, messages, maxTokens, temperature, jsonMode, false);

        // 2. 设置认证头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        setAuthHeaders(headers, config);

        // 3. 创建独立的 RestTemplate（避免线程安全问题）
        int timeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : 30000;
        RestTemplate rt = createRestTemplate(timeout);

        // 4. 执行请求
        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
        ResponseEntity<String> response = rt.postForEntity(config.getApiUrl(), entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful())
        {
            throw new IOException("HTTP " + response.getStatusCode().value()
                    + "，请检查 API 地址和密钥。响应: " + response.getBody());
        }

        // 5. 解析响应
        String content = parseNonStreamingResponse(response.getBody(), isAnthropic);
        if (content == null)
        {
            throw new IOException("API 返回异常：无法解析响应内容，可能接口地址不正确。响应: "
                    + (response.getBody() != null ? response.getBody().substring(0, Math.min(response.getBody().length(), 200)) : "null"));
        }
        return content;
    }

    // ===================================================================
    // 流式调用（返回 HttpURLConnection，由调用方逐行读取 SSE）
    // ===================================================================

    /**
     * 建立流式 SSE 连接，返回 HttpURLConnection。
     * 调用方负责读取 InputStream 并逐行调用 {@link #parseStreamLine} 解析。
     *
     * @param config      模型配置
     * @param messages    消息列表 [{role, content}, ...]
     * @param maxTokens   最大输出 token 数
     * @param temperature 温度参数（可为 null）
     * @return 已建立连接的 HttpURLConnection（已写入请求体）
     * @throws IOException 连接失败
     */
    public HttpURLConnection callStreaming(NewsModelConfig config, List<Map<String, String>> messages,
                                           Integer maxTokens, Double temperature)
            throws IOException
    {
        // 1. 构建请求体
        JSONObject body = buildRequestBody(config, messages, maxTokens, temperature, null, true);

        // 2. 建立连接
        int timeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : 30000;
        URL url = new URL(config.getApiUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(180_000);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        setAuthHeaders(conn, config);

        // 3. 写入请求体
        try (OutputStream reqOut = conn.getOutputStream())
        {
            reqOut.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
            reqOut.flush();
        }

        return conn;
    }

    // ===================================================================
    // SSE 流式行解析
    // ===================================================================

    /**
     * 解析一行 SSE 数据，提取增量文本内容
     *
     * @param line      SSE 原始行（如 "data: {...}"）
     * @param apiFormat API 格式
     * @return 增量文本，如果不是内容行则返回 null
     */
    public String parseStreamLine(String line, String apiFormat)
    {
        if (line == null || line.isEmpty()) return null;
        if (!line.startsWith("data:")) return null;

        String dataStr = line.substring("data:".length()).trim();

        // [DONE] 标记
        if ("[DONE]".equals(dataStr)) return null;

        try
        {
            JSONObject parsed = JSON.parseObject(dataStr);
            boolean isAnthropic = FORMAT_ANTHROPIC.equalsIgnoreCase(apiFormat);

            if (isAnthropic)
            {
                String type = parsed.getString("type");
                if ("content_block_delta".equals(type))
                {
                    JSONObject delta = parsed.getJSONObject("delta");
                    if (delta != null && "text_delta".equals(delta.getString("type")))
                    {
                        return delta.getString("text");
                    }
                }
                // 其他事件类型（message_start, content_block_start, content_block_stop,
                // message_delta, message_stop, ping）不携带内容
                return null;
            }
            else
            {
                // OpenAI 格式：choices[0].delta.content
                JSONArray choices = parsed.getJSONArray("choices");
                if (choices != null && !choices.isEmpty())
                {
                    JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                    if (delta != null)
                    {
                        return delta.getString("content");
                    }
                }
                return null;
            }
        }
        catch (Exception e)
        {
            log.debug("parseStreamLine 解析异常（跳过）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断 SSE 行是否为流结束标记
     *
     * @param line      SSE 原始行
     * @param apiFormat API 格式
     * @return true 表示流已结束
     */
    public boolean isStreamDone(String line, String apiFormat)
    {
        if (line == null || !line.startsWith("data:")) return false;

        String dataStr = line.substring("data:".length()).trim();

        if ("[DONE]".equals(dataStr)) return true;

        boolean isAnthropic = FORMAT_ANTHROPIC.equalsIgnoreCase(apiFormat);
        if (isAnthropic)
        {
            try
            {
                JSONObject parsed = JSON.parseObject(dataStr);
                return "message_stop".equals(parsed.getString("type"));
            }
            catch (Exception e)
            {
                return false;
            }
        }

        return false;
    }

    // ===================================================================
    // 内部方法
    // ===================================================================

    /**
     * 构建请求体 JSON
     */
    private JSONObject buildRequestBody(NewsModelConfig config, List<Map<String, String>> messages,
                                        Integer maxTokens, Double temperature, Boolean jsonMode,
                                        boolean stream)
    {
        boolean isAnthropic = FORMAT_ANTHROPIC.equalsIgnoreCase(config.getApiFormat());
        JSONObject body = new JSONObject();

        body.put("model", config.getModelName());
        body.put("max_tokens", maxTokens != null ? maxTokens : 2000);
        body.put("stream", stream);

        if (temperature != null)
        {
            body.put("temperature", temperature);
        }

        // Anthropic: 将 system 消息提取为顶层 system 字段
        if (isAnthropic)
        {
            StringBuilder systemContent = new StringBuilder();
            JSONArray nonSystemMessages = new JSONArray();

            for (Map<String, String> msg : messages)
            {
                if ("system".equals(msg.get("role")))
                {
                    if (systemContent.length() > 0) systemContent.append("\n\n");
                    systemContent.append(msg.get("content"));
                }
                else
                {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("role", msg.get("role"));
                    msgObj.put("content", msg.get("content"));
                    nonSystemMessages.add(msgObj);
                }
            }

            if (systemContent.length() > 0)
            {
                body.put("system", systemContent.toString());
            }
            body.put("messages", nonSystemMessages);
        }
        else
        {
            // OpenAI：直接放入 messages 数组
            JSONArray messagesArray = new JSONArray();
            for (Map<String, String> msg : messages)
            {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.get("role"));
                msgObj.put("content", msg.get("content"));
                messagesArray.add(msgObj);
            }
            body.put("messages", messagesArray);

            // OpenAI JSON 模式
            if (Boolean.TRUE.equals(jsonMode))
            {
                JSONObject rf = new JSONObject();
                rf.put("type", "json_object");
                body.put("response_format", rf);
            }
        }

        return body;
    }

    /**
     * 为 RestTemplate 设置认证头
     */
    private void setAuthHeaders(HttpHeaders headers, NewsModelConfig config)
    {
        if (FORMAT_ANTHROPIC.equalsIgnoreCase(config.getApiFormat()))
        {
            headers.set("x-api-key", config.getApiKey());
            headers.set("anthropic-version", ANTHROPIC_VERSION);
        }
        else
        {
            headers.set("Authorization", "Bearer " + config.getApiKey());
        }
    }

    /**
     * 为 HttpURLConnection 设置认证头
     */
    private void setAuthHeaders(HttpURLConnection conn, NewsModelConfig config)
    {
        if (FORMAT_ANTHROPIC.equalsIgnoreCase(config.getApiFormat()))
        {
            conn.setRequestProperty("x-api-key", config.getApiKey());
            conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
        }
        else
        {
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        }
    }

    /**
     * 解析非流式响应，提取文本内容
     */
    private String parseNonStreamingResponse(String responseBody, boolean isAnthropic)
    {
        JSONObject respJson = JSON.parseObject(responseBody);

        if (isAnthropic)
        {
            // Anthropic: { content: [{ type: "text", text: "..." }, ...] }
            // 注意：部分模型（如 deepseek-v4-pro）会在 content 数组中先返回
            // thinking 块，需要遍历找到 type="text" 的元素
            JSONArray content = respJson.getJSONArray("content");
            if (content != null && !content.isEmpty())
            {
                for (int i = 0; i < content.size(); i++)
                {
                    JSONObject block = content.getJSONObject(i);
                    if ("text".equals(block.getString("type")))
                    {
                        return block.getString("text");
                    }
                }
            }
        }
        else
        {
            // OpenAI: { choices: [{ message: { content: "..." } }] }
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices != null && !choices.isEmpty())
            {
                return choices.getJSONObject(0).getJSONObject("message").getString("content");
            }
        }
        return null;
    }

    /**
     * 创建独立的 RestTemplate（避免线程安全问题）
     */
    private RestTemplate createRestTemplate(int timeout)
    {
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(new SimpleClientHttpRequestFactory()
        {{
            setConnectTimeout(timeout);
            setReadTimeout(timeout);
        }});
        return rt;
    }
}
