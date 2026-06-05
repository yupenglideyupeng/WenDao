package com.wendao.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.wendao.common.core.domain.BaseEntity;

/**
 * 新闻关键词表 news_keyword
 *
 * @author wendao
 */
public class NewsKeyword extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关键词 */
    private String text;

    /** 分类 */
    private String category;

    /** 是否启用：1=启用 0=停用 */
    private Integer isActive;

    /** 抓取间隔(分钟) */
    private Integer fetchInterval;

    /** 上次抓取时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFetchTime;

    /** 相关性阈值,低于此值自动下架 */
    private Integer relevanceThreshold;

    /** 扩展查询词列表(JSON) */
    private String expandQueries;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public Integer getFetchInterval() { return fetchInterval; }
    public void setFetchInterval(Integer fetchInterval) { this.fetchInterval = fetchInterval; }

    public Date getLastFetchTime() { return lastFetchTime; }
    public void setLastFetchTime(Date lastFetchTime) { this.lastFetchTime = lastFetchTime; }

    public Integer getRelevanceThreshold() { return relevanceThreshold; }
    public void setRelevanceThreshold(Integer relevanceThreshold) { this.relevanceThreshold = relevanceThreshold; }

    public String getExpandQueries() { return expandQueries; }
    public void setExpandQueries(String expandQueries) { this.expandQueries = expandQueries; }

    @Override
    public String toString() {
        return new org.apache.commons.lang3.builder.ToStringBuilder(this, org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("text", getText())
            .append("category", getCategory())
            .append("isActive", getIsActive())
            .toString();
    }
}
