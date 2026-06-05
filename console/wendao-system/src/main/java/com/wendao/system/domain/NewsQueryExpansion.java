package com.wendao.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 查询扩展词表 news_query_expansion
 *
 * @author wendao
 */
public class NewsQueryExpansion
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 关联 news_keyword.id */
    private Long keywordId;

    /** 扩展词 */
    private String expandedTerm;

    /** 扩展类型：SYNONYM/RELATED/AI_GENERATED */
    private String expansionType;

    /** 是否启用 */
    private Integer isActive;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }

    public String getExpandedTerm() { return expandedTerm; }
    public void setExpandedTerm(String expandedTerm) { this.expandedTerm = expandedTerm; }

    public String getExpansionType() { return expansionType; }
    public void setExpansionType(String expansionType) { this.expansionType = expansionType; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
