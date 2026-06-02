package com.ruoyi.system.event;

import java.util.List;
import org.springframework.context.ApplicationEvent;
import com.ruoyi.system.domain.NewsArticle;

/**
 * 新闻抓取完成事件
 *
 * @author ruoyi
 */
public class NewsFetchedEvent extends ApplicationEvent
{
    private static final long serialVersionUID = 1L;

    private final List<NewsArticle> articles;

    public NewsFetchedEvent(Object source, List<NewsArticle> articles)
    {
        super(source);
        this.articles = articles;
    }

    public List<NewsArticle> getArticles()
    {
        return articles;
    }
}
