package com.wendao.web.controller.news;

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
import com.wendao.common.annotation.Log;
import com.wendao.common.core.controller.BaseController;
import com.wendao.common.core.domain.AjaxResult;
import com.wendao.common.core.page.TableDataInfo;
import com.wendao.common.enums.BusinessType;
import com.wendao.system.domain.NewsKeyword;
import com.wendao.system.service.INewsKeywordService;

/**
 * 新闻关键词 信息操作处理
 *
 * @author wendao
 */
@RestController
@RequestMapping("/news/keyword")
public class NewsKeywordController extends BaseController
{
    @Autowired
    private INewsKeywordService keywordService;

    @PreAuthorize("@ss.hasPermi('news:keyword:list')")
    @Log(title = "新闻关键词", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/list")
    public TableDataInfo list(NewsKeyword keyword)
    {
        startPage();
        List<NewsKeyword> list = keywordService.selectKeywordList(keyword);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('news:keyword:query')")
    @Log(title = "新闻关键词", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(keywordService.selectKeywordById(id));
    }

    @PreAuthorize("@ss.hasPermi('news:keyword:add')")
    @Log(title = "新闻关键词", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody NewsKeyword keyword)
    {
        keyword.setCreateBy(getUsername());
        return toAjax(keywordService.insertKeyword(keyword));
    }

    @PreAuthorize("@ss.hasPermi('news:keyword:edit')")
    @Log(title = "新闻关键词", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody NewsKeyword keyword)
    {
        keyword.setUpdateBy(getUsername());
        return toAjax(keywordService.updateKeyword(keyword));
    }

    @PreAuthorize("@ss.hasPermi('news:keyword:remove')")
    @Log(title = "新闻关键词", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(keywordService.deleteKeywordByIds(ids));
    }
}
