package com.wendao.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.wendao.common.core.domain.BaseEntity;

/**
 * 新闻解读记录表 news_interpretation
 *
 * @author wendao
 */
public class NewsInterpretation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联新闻文章ID */
    private Long articleId;

    /** 使用的提示词配置ID */
    private Long promptConfigId;

    /** 解读时的提示词快照（防止提示词修改后无法追溯） */
    private String promptSnapshot;

    /** 解读结果内容（Markdown格式） */
    private String content;

    /** 解读状态：0=进行中 1=完成 2=失败 */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    /** 消耗token数 */
    private Integer tokensUsed;

    /** 使用的AI模型名称 */
    private String modelName;

    /** 第几次解读（从1开始递增） */
    private Integer interpretCount;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getArticleId()
    {
        return articleId;
    }

    public void setArticleId(Long articleId)
    {
        this.articleId = articleId;
    }

    public Long getPromptConfigId()
    {
        return promptConfigId;
    }

    public void setPromptConfigId(Long promptConfigId)
    {
        this.promptConfigId = promptConfigId;
    }

    public String getPromptSnapshot()
    {
        return promptSnapshot;
    }

    public void setPromptSnapshot(String promptSnapshot)
    {
        this.promptSnapshot = promptSnapshot;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public Integer getTokensUsed()
    {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed)
    {
        this.tokensUsed = tokensUsed;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public Integer getInterpretCount()
    {
        return interpretCount;
    }

    public void setInterpretCount(Integer interpretCount)
    {
        this.interpretCount = interpretCount;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
