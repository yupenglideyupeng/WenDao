package com.wendao.system.service;

import java.util.List;
import com.wendao.system.domain.NewsArticle;

/**
 * 新闻抓取 服务层
 *
 * @author wendao
 */
public interface INewsFetcherService
{
    /**
     * 从所有启用的新闻源抓取新闻
     */
    public int fetchAllSources();

    /**
     * 从指定新闻源抓取
     */
    public int fetchFromSource(Long sourceId);

    /**
     * 推送未推送的文章到WebSocket
     */
    public void pushUnpushedArticles(List<NewsArticle> articles);
}
