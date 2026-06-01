package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 新闻文章表 news_article
 *
 * @author ruoyi
 */
public class NewsArticle extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 新闻来源ID */
    private Long sourceId;

    /** 来源名称(冗余) */
    private String sourceName;

    /** 文章标题 */
    private String title;

    /** AI生成的摘要 */
    private String summary;

    /** 原始内容 */
    private String content;

    /** 原始链接 */
    private String originalUrl;

    /** 语言：zh/en/ja */
    private String language;

    /** AI提取的标签(JSON数组) */
    private String tags;

    /** 情感分析：positive/negative/neutral */
    private String sentiment;

    /** AI提取关键词 */
    private String keywords;

    /** 新闻发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    /** 抓取入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date fetchTime;

    /** 是否已推送：0=未推送 1=已推送 */
    private String isPushed;

    /** 推送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pushTime;

    /** 阅读次数 */
    private Integer readCount;

    /** 状态：0=正常 1=下架 */
    private String status;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getSourceId()
    {
        return sourceId;
    }

    public void setSourceId(Long sourceId)
    {
        this.sourceId = sourceId;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public void setSourceName(String sourceName)
    {
        this.sourceName = sourceName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getOriginalUrl()
    {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl)
    {
        this.originalUrl = originalUrl;
    }

    public String getLanguage()
    {
        return language;
    }

    public void setLanguage(String language)
    {
        this.language = language;
    }

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public String getSentiment()
    {
        return sentiment;
    }

    public void setSentiment(String sentiment)
    {
        this.sentiment = sentiment;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public Date getPublishTime()
    {
        return publishTime;
    }

    public void setPublishTime(Date publishTime)
    {
        this.publishTime = publishTime;
    }

    public Date getFetchTime()
    {
        return fetchTime;
    }

    public void setFetchTime(Date fetchTime)
    {
        this.fetchTime = fetchTime;
    }

    public String getIsPushed()
    {
        return isPushed;
    }

    public void setIsPushed(String isPushed)
    {
        this.isPushed = isPushed;
    }

    public Date getPushTime()
    {
        return pushTime;
    }

    public void setPushTime(Date pushTime)
    {
        this.pushTime = pushTime;
    }

    public Integer getReadCount()
    {
        return readCount;
    }

    public void setReadCount(Integer readCount)
    {
        this.readCount = readCount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString() {
        return new org.apache.commons.lang3.builder.ToStringBuilder(this, org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("sourceId", getSourceId())
            .append("sourceName", getSourceName())
            .append("title", getTitle())
            .append("originalUrl", getOriginalUrl())
            .append("language", getLanguage())
            .append("sentiment", getSentiment())
            .append("isPushed", getIsPushed())
            .append("readCount", getReadCount())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
