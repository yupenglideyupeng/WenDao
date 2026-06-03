package com.ruoyi.web.controller.news;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.framework.websocket.NewsWebSocketHandler;
import com.ruoyi.system.domain.NewsArticle;
import com.ruoyi.system.service.INewsArticleService;

/**
 * 新闻文章 信息操作处理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/news/article")
public class NewsArticleController extends BaseController
{
    @Autowired
    private INewsArticleService newsArticleService;

    @Autowired
    private NewsWebSocketHandler webSocketHandler;

    /**
     * 获取新闻文章列表
     */
    @PreAuthorize("@ss.hasPermi('news:article:list')")
    @Log(title = "新闻文章", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/list")
    public TableDataInfo list(NewsArticle article)
    {
        startPage();
        List<NewsArticle> list = newsArticleService.selectArticleList(article);
        return getDataTable(list);
    }

    /**
     * 根据编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('news:article:query')")
    @Log(title = "新闻文章", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(newsArticleService.selectArticleById(id));
    }

    /**
     * 修改新闻文章
     */
    @PreAuthorize("@ss.hasPermi('news:article:edit')")
    @Log(title = "新闻文章", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody NewsArticle article)
    {
        article.setUpdateBy(getUsername());
        return toAjax(newsArticleService.updateArticle(article));
    }

    /**
     * 删除新闻文章
     */
    @PreAuthorize("@ss.hasPermi('news:article:remove')")
    @Log(title = "新闻文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(newsArticleService.deleteArticleByIds(ids));
    }

    /**
     * 手动推送文章到WebSocket
     */
    @PreAuthorize("@ss.hasPermi('news:article:push')")
    @Log(title = "新闻推送", businessType = BusinessType.OTHER)
    @PostMapping("/push/{id}")
    public AjaxResult push(@PathVariable Long id)
    {
        NewsArticle article = newsArticleService.selectArticleById(id);
        if (article == null)
        {
            return error("文章不存在");
        }
        // 标记为已推送
        newsArticleService.markAsPushed(id);
        // 通过WebSocket广播推送
        webSocketHandler.broadcastArticle(article);
        return success();
    }
}
