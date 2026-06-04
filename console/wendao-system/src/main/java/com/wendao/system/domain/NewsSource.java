package com.wendao.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.wendao.common.core.domain.BaseEntity;

/**
 * 新闻来源表 news_source
 *
 * @author wendao
 */
public class NewsSource extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 来源名称 */
    private String name;

    /** 来源类型：0=国内 1=国外 */
    private String type;

    /** 新闻源地址 */
    private String url;

    /** 抓取方式：RSS/API/CRAWL */
    private String fetchType;

    /** 抓取间隔(分钟) */
    private Integer fetchInterval;

    /** 额外配置(JSON) */
    private String fetchConfig;

    /** 状态：0=启用 1=停用 */
    private String status;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @NotBlank(message = "来源名称不能为空")
    @Size(min = 0, max = 100, message = "来源名称不能超过100个字符")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    @NotBlank(message = "新闻源地址不能为空")
    @Size(min = 0, max = 500, message = "新闻源地址不能超过500个字符")
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getFetchType()
    {
        return fetchType;
    }

    public void setFetchType(String fetchType)
    {
        this.fetchType = fetchType;
    }

    public Integer getFetchInterval()
    {
        return fetchInterval;
    }

    public void setFetchInterval(Integer fetchInterval)
    {
        this.fetchInterval = fetchInterval;
    }

    public String getFetchConfig()
    {
        return fetchConfig;
    }

    public void setFetchConfig(String fetchConfig)
    {
        this.fetchConfig = fetchConfig;
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
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("type", getType())
            .append("url", getUrl())
            .append("fetchType", getFetchType())
            .append("fetchInterval", getFetchInterval())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
