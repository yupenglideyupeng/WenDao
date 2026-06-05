package com.wendao.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 新闻抓取配置属性（查询扩展、相关性阈值等）
 *
 * @author wendao
 */
@ConfigurationProperties(prefix = "news")
public class NewsFetchProperties
{
    private QueryExpansion queryExpansion = new QueryExpansion();
    private Relevance relevance = new Relevance();

    public QueryExpansion getQueryExpansion() { return queryExpansion; }
    public void setQueryExpansion(QueryExpansion queryExpansion) { this.queryExpansion = queryExpansion; }

    public Relevance getRelevance() { return relevance; }
    public void setRelevance(Relevance relevance) { this.relevance = relevance; }

    public static class QueryExpansion
    {
        /** 是否启用查询扩展 */
        private boolean enabled = true;
        /** 每个关键词最多扩展几个词 */
        private int maxTerms = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxTerms() { return maxTerms; }
        public void setMaxTerms(int maxTerms) { this.maxTerms = maxTerms; }
    }

    public static class Relevance
    {
        /** 全局默认相关性阈值 */
        private int defaultThreshold = 40;

        public int getDefaultThreshold() { return defaultThreshold; }
        public void setDefaultThreshold(int defaultThreshold) { this.defaultThreshold = defaultThreshold; }
    }
}
