package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 新闻类型配置表 news_type_config
 *
 * @author ruoyi
 */
public class NewsTypeConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String typeName;
    private String typeCode;
    private String description;
    private Integer sortOrder;
    private Integer isActive;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
}
