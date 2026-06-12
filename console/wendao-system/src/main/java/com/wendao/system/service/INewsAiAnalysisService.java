package com.wendao.system.service;

import java.util.List;

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

    /**
     * 批量同步评分（入库前调用），一次 AI 调用评多篇
     * @param articles 待评分文章列表（至少需要有 title）
     * @return 评分结果列表（与输入同顺序），AI 失败返回空列表
     */
    List<RelevanceScore> batchScoreRelevance(List<NewsArticle> articles);

    /**
     * 批量相关性评分结果
     */
    class RelevanceScore
    {
        private int index;
        private int relevance;
        private boolean isReal;
        private String importance;
        private String reason;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public int getRelevance() { return relevance; }
        public void setRelevance(int relevance) { this.relevance = relevance; }
        public boolean isReal() { return isReal; }
        public void setReal(boolean isReal) { this.isReal = isReal; }
        public String getImportance() { return importance; }
        public void setImportance(String importance) { this.importance = importance; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
