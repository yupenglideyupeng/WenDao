package com.wendao.system.domain;

import java.util.List;
import com.wendao.common.core.domain.BaseEntity;

/**
 * 新闻提示词配置表 news_prompt_config
 *
 * @author wendao
 */
public class NewsPromptConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String promptType;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Integer isActive;

    /** 关联的新闻类型名称列表（非数据库字段，展示用） */
    private String typeNames;

    /** 关联的新闻类型ID列表（多对多，持久化到中间表） */
    private List<Long> typeConfigIds;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPromptType() { return promptType; }
    public void setPromptType(String promptType) { this.promptType = promptType; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getTypeNames() { return typeNames; }
    public void setTypeNames(String typeNames) { this.typeNames = typeNames; }
    public List<Long> getTypeConfigIds() { return typeConfigIds; }
    public void setTypeConfigIds(List<Long> typeConfigIds) { this.typeConfigIds = typeConfigIds; }
}
