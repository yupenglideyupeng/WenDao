package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.domain.NewsPromptConfig;
import com.ruoyi.system.mapper.NewsPromptConfigMapper;
import com.ruoyi.system.service.INewsPromptConfigService;

@Service
public class NewsPromptConfigServiceImpl implements INewsPromptConfigService
{
    @Autowired
    private NewsPromptConfigMapper mapper;

    @Override
    public NewsPromptConfig selectById(Long id)
    {
        NewsPromptConfig cfg = mapper.selectById(id);
        if (cfg != null)
        {
            cfg.setTypeConfigIds(mapper.selectTypeConfigIds(id));
        }
        return cfg;
    }

    @Override
    public List<NewsPromptConfig> selectList(NewsPromptConfig c)
    {
        List<NewsPromptConfig> list = mapper.selectList(c);
        for (NewsPromptConfig cfg : list)
        {
            cfg.setTypeConfigIds(mapper.selectTypeConfigIds(cfg.getId()));
        }
        return list;
    }

    @Override
    public NewsPromptConfig selectMatch(Long typeConfigId, String promptType)
    {
        // 1. 精确匹配：指定类型 + promptType
        if (typeConfigId != null)
        {
            NewsPromptConfig result = mapper.selectMatch(typeConfigId, promptType);
            if (result != null) return result;
        }
        // 2. 降级：只按 promptType 查（不限类型）
        List<NewsPromptConfig> all = mapper.selectList(null);
        for (NewsPromptConfig cfg : all)
        {
            if (promptType.equals(cfg.getPromptType()) && cfg.getIsActive() != null && cfg.getIsActive() == 1)
            {
                return cfg;
            }
        }
        return null;
    }

    @Override
    @Transactional
    public int insert(NewsPromptConfig c)
    {
        int rows = mapper.insert(c);
        // 插入类型关联
        if (c.getTypeConfigIds() != null && !c.getTypeConfigIds().isEmpty())
        {
            mapper.insertTypeRelations(c.getId(), c.getTypeConfigIds());
        }
        return rows;
    }

    @Override
    @Transactional
    public int update(NewsPromptConfig c)
    {
        // 先删旧关联，再插新的
        mapper.deleteTypeRelations(c.getId());
        if (c.getTypeConfigIds() != null && !c.getTypeConfigIds().isEmpty())
        {
            mapper.insertTypeRelations(c.getId(), c.getTypeConfigIds());
        }
        return mapper.update(c);
    }

    @Override
    @Transactional
    public int deleteByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            mapper.deleteTypeRelations(id);
        }
        return mapper.deleteByIds(ids);
    }
}
