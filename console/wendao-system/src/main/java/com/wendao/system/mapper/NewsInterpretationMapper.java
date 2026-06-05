package com.wendao.system.mapper;

import com.wendao.system.domain.NewsInterpretation;

import java.util.List;

/**
 * 新闻解读记录 数据层
 *
 * @author wendao
 */
public interface NewsInterpretationMapper
{
    /**
     * 查询最新一条解读记录（按article_id，取id最大的一条）
     */
    NewsInterpretation selectLatestByArticleId(Long articleId);

    /**
     * 查询某篇文章的所有解读记录（按时间倒序）
     */
    List<NewsInterpretation> selectListByArticleId(Long articleId);

    /**
     * 新增解读记录
     */
    int insert(NewsInterpretation interpretation);

    /**
     * 更新解读记录（状态、内容、token等）
     */
    int update(NewsInterpretation interpretation);
}
