package com.wendao.system.mapper;

import java.util.List;
import com.wendao.system.domain.NewsSource;

/**
 * 新闻来源 数据层
 *
 * @author wendao
 */
public interface NewsSourceMapper
{
    /**
     * 查询新闻来源
     */
    public NewsSource selectSourceById(Long id);

    /**
     * 查询新闻来源列表
     */
    public List<NewsSource> selectSourceList(NewsSource source);

    /**
     * 查询所有启用的新闻来源
     */
    public List<NewsSource> selectEnabledSources();

    /**
     * 新增新闻来源
     */
    public int insertSource(NewsSource source);

    /**
     * 修改新闻来源
     */
    public int updateSource(NewsSource source);

    /**
     * 删除新闻来源
     */
    public int deleteSourceById(Long id);

    /**
     * 批量删除新闻来源
     */
    public int deleteSourceByIds(Long[] ids);
}
