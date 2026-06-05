package com.wendao.system.service;

import com.wendao.system.domain.NewsInterpretation;

import java.util.List;

/**
 * 新闻解读记录 服务层接口
 *
 * @author wendao
 */
public interface INewsInterpretationService
{
    /**
     * 查询某篇文章最新一条解读记录
     *
     * @param articleId 文章ID
     * @return 解读记录（可能为null）
     */
    NewsInterpretation selectLatestByArticleId(Long articleId);

    /**
     * 查询某篇文章的所有解读记录（按时间倒序）
     *
     * @param articleId 文章ID
     * @return 解读记录列表
     */
    List<NewsInterpretation> selectListByArticleId(Long articleId);

    /**
     * 新增解读记录
     *
     * @param interpretation 解读记录
     * @return 插入行数
     */
    int insert(NewsInterpretation interpretation);

    /**
     * 更新解读记录
     *
     * @param interpretation 解读记录
     * @return 更新行数
     */
    int update(NewsInterpretation interpretation);
}
