package com.wendao.web.controller.news;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wendao.common.annotation.Log;
import com.wendao.common.core.controller.BaseController;
import com.wendao.common.core.domain.AjaxResult;
import com.wendao.common.enums.BusinessType;
import com.wendao.framework.websocket.NewsWebSocketHandler;
import com.wendao.system.domain.NewsArticle;
import com.wendao.system.service.INewsArticleService;

/**
 * 新闻大屏 数据接口
 *
 * @author wendao
 */
@RestController
@RequestMapping("/news/dashboard")
public class NewsDashboardController extends BaseController
{
    @Autowired
    private INewsArticleService newsArticleService;

    @Autowired
    private NewsWebSocketHandler webSocketHandler;

    /**
     * 获取大屏统计数据
     */
    @Log(title = "新闻大屏", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        Map<String, Object> stats = newsArticleService.getDashboardStats();
        return success(stats);
    }

    /**
     * 获取最新文章列表
     */
    @Log(title = "新闻大屏", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/latest")
    public AjaxResult getLatestArticles(@RequestParam(defaultValue = "20") int limit)
    {
        NewsArticle query = new NewsArticle();
        query.setStatus("0");
        query.getParams().put("pageNum", "1");
        query.getParams().put("pageSize", String.valueOf(Math.min(limit, 50)));
        startPage();
        List<NewsArticle> list = newsArticleService.selectArticleList(query);
        return success(list);
    }

    /**
     * 获取在线客户端数量
     */
    @Log(title = "新闻大屏", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/onlineCount")
    public AjaxResult getOnlineCount()
    {
        int count = webSocketHandler.getOnlineCount();
        return success(Map.of("onlineCount", count));
    }
}
