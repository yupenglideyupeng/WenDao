package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.NewsPromptConfig;

public interface NewsPromptConfigMapper
{
    public NewsPromptConfig selectById(Long id);
    public List<NewsPromptConfig> selectList(NewsPromptConfig config);
    /** 按新闻类型+提示词类型查找匹配的提示词（通过中间表） */
    public NewsPromptConfig selectMatch(Long typeConfigId, String promptType);
    public int insert(NewsPromptConfig config);
    public int update(NewsPromptConfig config);
    public int deleteById(Long id);
    public int deleteByIds(Long[] ids);
    /** 插入提示词-类型关联 */
    public int insertTypeRelations(Long promptConfigId, List<Long> typeConfigIds);
    /** 删除提示词的所有类型关联 */
    public int deleteTypeRelations(Long promptConfigId);
    /** 查询提示词关联的类型ID列表 */
    public List<Long> selectTypeConfigIds(Long promptConfigId);
    /** 查询提示词关联的类型名称（逗号拼接） */
    public String selectTypeNames(Long promptConfigId);
}
