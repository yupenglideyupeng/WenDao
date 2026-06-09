package com.wendao.system.mapper;

import com.wendao.system.domain.NewsModelConfig;

import java.util.List;

/**
 * AI模型配置 数据层
 *
 * @author wendao
 */
public interface NewsModelConfigMapper
{
    /**
     * 条件查询列表
     */
    List<NewsModelConfig> selectList(NewsModelConfig config);

    /**
     * 按ID查询
     */
    NewsModelConfig selectById(Long id);

    /**
     * 按适用场景查询激活的配置（按优先级升序）
     */
    List<NewsModelConfig> selectByUsageType(String usageType);

    /**
     * 新增
     */
    int insert(NewsModelConfig config);

    /**
     * 修改
     */
    int update(NewsModelConfig config);

    /**
     * 批量删除
     */
    int deleteByIds(Long[] ids);
}
