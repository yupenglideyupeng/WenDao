package com.wendao.system.service;

import com.wendao.system.domain.NewsModelConfig;

import java.util.List;

/**
 * AI模型配置 服务层接口
 *
 * @author wendao
 */
public interface INewsModelConfigService
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
     * 按适用场景查询激活的配置（按优先级升序，调用方依次重试）
     */
    List<NewsModelConfig> selectByUsageType(String usageType);

    /**
     * 新增（自动加密 apiKey）
     */
    int insert(NewsModelConfig config);

    /**
     * 修改（如 apiKey 为脱敏值则保留原值不更新）
     */
    int update(NewsModelConfig config);

    /**
     * 批量删除
     */
    int deleteByIds(Long[] ids);

    /**
     * 测试模型连接
     * @return { success: true/false, message: "xxx", modelName: "xxx" }
     */
    java.util.Map<String, Object> testConnection(Long id);
}
