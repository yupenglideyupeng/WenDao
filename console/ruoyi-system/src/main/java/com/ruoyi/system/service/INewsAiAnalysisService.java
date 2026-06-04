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
     */
    void analyzeAsync(NewsArticle article);

    /**
     * 异步深度分析（关键词监控场景），填充isReal/relevance/importance等字段
     * @param article 文章
     * @param keyword 监控关键词
     */
    void analyzeDeepAsync(NewsArticle article, String keyword);
}
