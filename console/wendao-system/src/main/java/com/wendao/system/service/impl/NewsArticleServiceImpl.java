package com.wendao.system.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.mapper.NewsArticleMapper;
import com.wendao.system.service.INewsArticleService;

/**
 * 新闻文章 服务层实现
 *
 * @author wendao
 */
@Service
public class NewsArticleServiceImpl implements INewsArticleService
{
    @Autowired
    private NewsArticleMapper articleMapper;

    @Override
    public NewsArticle selectArticleById(Long id)
    {
        return articleMapper.selectArticleById(id);
    }

    @Override
    public List<NewsArticle> selectArticleList(NewsArticle article)
    {
        return articleMapper.selectArticleList(article);
    }

    @Override
    public List<NewsArticle> selectUnpushedArticles()
    {
        return articleMapper.selectUnpushedArticles();
    }

    @Override
    public Map<String, Object> getDashboardStats()
    {
        Map<String, Object> stats = new HashMap<>();
        // 基础统计（含国内/国外计数）
        Map<String, Object> baseStats = articleMapper.selectDashboardStats();
        if (baseStats != null)
        {
            stats.putAll(baseStats);
        }
        // 来源分布
        List<Map<String, Object>> sourceDist = articleMapper.selectSourceDistribution();
        stats.put("sourceDistribution", sourceDist);
        // 情感分布
        List<Map<String, Object>> sentimentDist = articleMapper.selectSentimentDistribution();
        stats.put("sentimentDistribution", sentimentDist);
        // 24小时趋势
        List<Map<String, Object>> timeline = articleMapper.select24HourTimeline();
        stats.put("timelineData", timeline);
        // 热门标签
        List<Map<String, Object>> hotTags = articleMapper.selectHotTags();
        stats.put("hotTags", hotTags);
        return stats;
    }

    @Override
    public int insertArticle(NewsArticle article)
    {
        return articleMapper.insertArticle(article);
    }

    @Override
    public int updateArticle(NewsArticle article)
    {
        return articleMapper.updateArticle(article);
    }

    @Override
    public int markAsPushed(Long id)
    {
        return articleMapper.markAsPushed(id);
    }

    @Override
    public int deleteArticleById(Long id)
    {
        return articleMapper.deleteArticleById(id);
    }

    @Override
    public int deleteArticleByIds(Long[] ids)
    {
        return articleMapper.deleteArticleByIds(ids);
    }
}
