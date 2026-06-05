package com.wendao.system.service;

import com.wendao.system.domain.NewsArticle;

/**
 * AI新闻分析 服务层接口
 *
 * @author wendao
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

    /**
     * 异步深度分析（带相关性阈值过滤）
     * @param article 文章
     * @param keyword 监控关键词（可为null，表示来源文章深度分析）
     * @param relevanceThreshold 相关性阈值，低于此值的文章自动下架（0=不过滤）
     */
    void analyzeDeepAsync(NewsArticle article, String keyword, int relevanceThreshold);
}
