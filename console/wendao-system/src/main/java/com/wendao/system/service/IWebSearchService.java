package com.wendao.system.service;

import java.util.List;
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

    /**
     * 通过关键词搜索指定搜索引擎
     *
     * @param keyword    搜索关键词
     * @param engineName 引擎名称：Bing搜索 / 搜狗搜索 / B站搜索 / 微博热搜
     */
    List<NewsArticle> searchByEngine(String keyword, String engineName);
}
