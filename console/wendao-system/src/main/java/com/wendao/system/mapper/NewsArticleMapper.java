package com.wendao.system.mapper;

import java.util.List;
import java.util.Map;
import com.wendao.system.domain.NewsArticle;

/**
 * 新闻文章 数据层
 *
 * @author wendao
 */
public interface NewsArticleMapper
{
    /**
     * 查询文章信息
     */
    public NewsArticle selectArticleById(Long id);

    /**
     * 查询文章列表
     */
    public List<NewsArticle> selectArticleList(NewsArticle article);

    /**
     * 根据URL查询文章(去重)
     */
    public NewsArticle selectArticleByUrl(String originalUrl);

    /**
     * 查询未推送文章
     */
    public List<NewsArticle> selectUnpushedArticles();

    /**
     * 查询大屏统计数据
     */
    public Map<String, Object> selectDashboardStats();

    /**
     * 查询24小时趋势
     */
    public List<Map<String, Object>> select24HourTimeline();

    /**
     * 查询来源分布
     */
    public List<Map<String, Object>> selectSourceDistribution();

    /**
     * 查询情感分布
     */
    public List<Map<String, Object>> selectSentimentDistribution();

    /**
     * 查询热门标签
     */
    public List<Map<String, Object>> selectHotTags();

    /**
     * 新增文章
     */
    public int insertArticle(NewsArticle article);

    /**
     * 修改文章
     */
    public int updateArticle(NewsArticle article);

    /**
     * 标记为已推送
     */
    public int markAsPushed(Long id);

    /**
     * 删除文章
     */
    public int deleteArticleById(Long id);

    /**
     * 批量删除文章
     */
    public int deleteArticleByIds(Long[] ids);
}
