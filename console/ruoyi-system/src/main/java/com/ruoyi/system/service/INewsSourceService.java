package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.NewsSource;

/**
 * 新闻来源 服务层
 *
 * @author ruoyi
 */
public interface INewsSourceService
{
    public NewsSource selectSourceById(Long id);

    public List<NewsSource> selectSourceList(NewsSource source);

    public List<NewsSource> selectEnabledSources();

    public int insertSource(NewsSource source);

    public int updateSource(NewsSource source);

    public int deleteSourceById(Long id);

    public int deleteSourceByIds(Long[] ids);
}
