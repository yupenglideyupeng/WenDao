package com.ruoyi.system.service;

import com.ruoyi.system.domain.NewsArticle;

/**
 * AI新闻分析 服务层接口
 *
 * @author ruoyi
 */
public interface INewsAiAnalysisService
{
    /**
     * 异步分析文章内容，填充摘要、标签、情感、关键词字段
     * 分析完成后自动更新数据库
     *
     * @param article 已保存的文章（必须已设置id字段）
     */
    void analyzeAsync(NewsArticle article);
}
