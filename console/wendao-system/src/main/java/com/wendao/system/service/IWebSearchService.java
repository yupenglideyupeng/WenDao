package com.wendao.system.service;

import java.util.List;
import java.util.Map;
import com.wendao.system.domain.NewsArticle;

/**
 * 网页搜索服务（Jsoup爬虫）
 *
 * @author wendao
 */
public interface IWebSearchService
{
    /**
     * 通过关键词搜索多个搜索引擎，返回文章列表
     */
    List<NewsArticle> searchByKeyword(String keyword);
}
