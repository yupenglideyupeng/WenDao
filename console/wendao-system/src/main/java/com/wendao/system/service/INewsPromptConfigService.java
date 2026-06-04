package com.wendao.system.service;

import java.util.List;
import com.wendao.system.domain.NewsPromptConfig;

public interface INewsPromptConfigService
{
    public NewsPromptConfig selectById(Long id);
    public List<NewsPromptConfig> selectList(NewsPromptConfig config);
    /** 按新闻类型+提示词类型查找匹配的提示词（通过中间表），找不到则降级查找 */
    public NewsPromptConfig selectMatch(Long typeConfigId, String promptType);
    public int insert(NewsPromptConfig config);
    public int update(NewsPromptConfig config);
    public int deleteByIds(Long[] ids);
}
