package com.wendao.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI分析配置属性（通过 NewsAiConfig 的 @EnableConfigurationProperties 注册）
 *
 * @author wendao
 */
@ConfigurationProperties(prefix = "news.ai")
public class NewsAiProperties
{
    /** 是否启用AI分析 */
    private boolean enabled = true;

    /** AI API地址 */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    /** AI API密钥 */
    private String apiKey;

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 超时时间(毫秒) */
    private int timeout = 30000;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getApiUrl()
    {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl)
    {
        this.apiUrl = apiUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public int getTimeout()
    {
        return timeout;
    }

    public void setTimeout(int timeout)
    {
        this.timeout = timeout;
    }
}
