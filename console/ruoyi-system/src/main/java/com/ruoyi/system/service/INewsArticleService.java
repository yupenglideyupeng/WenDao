package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.NewsArticle;

/**
 * 新闻文章 服务层
 *
 * @author ruoyi
 */
public interface INewsArticleService
{
    public NewsArticle selectArticleById(Long id);

    public List<NewsArticle> selectArticleList(NewsArticle article);

    public List<NewsArticle> selectUnpushedArticles();

    public Map<String, Object> getDashboardStats();

    public int insertArticle(NewsArticle article);

    public int updateArticle(NewsArticle article);

    public int markAsPushed(Long id);

    public int deleteArticleById(Long id);

    public int deleteArticleByIds(Long[] ids);
}
