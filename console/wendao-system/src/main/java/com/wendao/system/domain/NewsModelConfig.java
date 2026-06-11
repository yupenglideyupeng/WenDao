package com.wendao.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wendao.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * AI模型配置表 news_model_config
 *
 * @author wendao
 */
public class NewsModelConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 配置名称 */
    private String name;

    /** 提供商：DEEPSEEK/SILICONFLOW/BAILIAN/ZHIPU/VOLCENGINE/CUSTOM */
    private String provider;

    /** API地址 */
    private String apiUrl;

    /** API密钥（AES加密存储，返回前端时脱敏） */
    private String apiKey;

    /** 模型名称 */
    private String modelName;

    /** 优先级，数字越小越优先，0=禁用 */
    private Integer priority;

    /** 默认最大输出token数 */
    private Integer maxTokens;

    /** 默认温度参数 */
    private BigDecimal temperature;

    /** 是否支持JSON结构化输出：0=否 1=是 */
    private Integer supportJsonMode;

    /** 是否支持流式输出：0=否 1=是 */
    private Integer supportStream;

    /** 适用场景：INTERPRET/ANALYSIS/EXPANSION/ALL */
    private String usageType;

    /** API格式：OPENAI / ANTHROPIC */
    private String apiFormat;

    /** 请求超时（毫秒） */
    private Integer timeoutMs;

    /** 失败重试次数 */
    private Integer retryCount;

    /** 是否启用：0=停用 1=启用 */
    private Integer isActive;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    // ======== getters/setters ========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public Integer getSupportJsonMode() { return supportJsonMode; }
    public void setSupportJsonMode(Integer supportJsonMode) { this.supportJsonMode = supportJsonMode; }

    public Integer getSupportStream() { return supportStream; }
    public void setSupportStream(Integer supportStream) { this.supportStream = supportStream; }

    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }

    public String getApiFormat() { return apiFormat; }
    public void setApiFormat(String apiFormat) { this.apiFormat = apiFormat; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    @Override
    public Date getUpdateTime() { return updateTime; }
    @Override
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
