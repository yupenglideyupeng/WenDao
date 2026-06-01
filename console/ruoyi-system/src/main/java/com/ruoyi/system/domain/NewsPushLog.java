package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 新闻推送记录表 news_push_log
 *
 * @author ruoyi
 */
public class NewsPushLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 文章ID */
    private Long articleId;

    /** 推送时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pushTime;

    /** 推送状态：0=成功 1=失败 */
    private String pushStatus;

    /** 推送方式 */
    private String pushType;

    /** 失败原因 */
    private String errorMsg;

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

    public Date getPushTime()
    {
        return pushTime;
    }

    public void setPushTime(Date pushTime)
    {
        this.pushTime = pushTime;
    }

    public String getPushStatus()
    {
        return pushStatus;
    }

    public void setPushStatus(String pushStatus)
    {
        this.pushStatus = pushStatus;
    }

    public String getPushType()
    {
        return pushType;
    }

    public void setPushType(String pushType)
    {
        this.pushType = pushType;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    @Override
    public String toString() {
        return new org.apache.commons.lang3.builder.ToStringBuilder(this, org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("articleId", getArticleId())
            .append("pushTime", getPushTime())
            .append("pushStatus", getPushStatus())
            .append("pushType", getPushType())
            .toString();
    }
}
