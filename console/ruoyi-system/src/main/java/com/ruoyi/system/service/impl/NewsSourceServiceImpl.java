package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.NewsSource;
import com.ruoyi.system.mapper.NewsSourceMapper;
import com.ruoyi.system.service.INewsSourceService;

/**
 * 新闻来源 服务层实现
 *
 * @author ruoyi
 */
@Service
public class NewsSourceServiceImpl implements INewsSourceService
{
    @Autowired
    private NewsSourceMapper sourceMapper;

    @Override
    public NewsSource selectSourceById(Long id)
    {
        return sourceMapper.selectSourceById(id);
    }

    @Override
    public List<NewsSource> selectSourceList(NewsSource source)
    {
        return sourceMapper.selectSourceList(source);
    }

    @Override
    public List<NewsSource> selectEnabledSources()
    {
        return sourceMapper.selectEnabledSources();
    }

    @Override
    public int insertSource(NewsSource source)
    {
        return sourceMapper.insertSource(source);
    }

    @Override
    public int updateSource(NewsSource source)
    {
        return sourceMapper.updateSource(source);
    }

    @Override
    public int deleteSourceById(Long id)
    {
        return sourceMapper.deleteSourceById(id);
    }

    @Override
    public int deleteSourceByIds(Long[] ids)
    {
        return sourceMapper.deleteSourceByIds(ids);
    }
}
