package com.ruoyi.web.controller.news;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import com.ruoyi.system.domain.NewsSource;
import com.ruoyi.system.service.INewsSourceService;

/**
 * 新闻来源 信息操作处理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/news/source")
public class NewsSourceController extends BaseController
{
    @Autowired
    private INewsSourceService newsSourceService;

    /**
     * 获取新闻来源列表
     */
    @PreAuthorize("@ss.hasPermi('news:source:list')")
    @Log(title = "新闻来源", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/list")
    public TableDataInfo list(NewsSource source)
    {
        startPage();
        List<NewsSource> list = newsSourceService.selectSourceList(source);
        return getDataTable(list);
    }

    /**
     * 根据编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('news:source:query')")
    @Log(title = "新闻来源", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(newsSourceService.selectSourceById(id));
    }

    /**
     * 新增新闻来源
     */
    @PreAuthorize("@ss.hasPermi('news:source:add')")
    @Log(title = "新闻来源", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody NewsSource source)
    {
        source.setCreateBy(getUsername());
        return toAjax(newsSourceService.insertSource(source));
    }

    /**
     * 修改新闻来源
     */
    @PreAuthorize("@ss.hasPermi('news:source:edit')")
    @Log(title = "新闻来源", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody NewsSource source)
    {
        source.setUpdateBy(getUsername());
        return toAjax(newsSourceService.updateSource(source));
    }

    /**
     * 删除新闻来源
     */
    @PreAuthorize("@ss.hasPermi('news:source:remove')")
    @Log(title = "新闻来源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(newsSourceService.deleteSourceByIds(ids));
    }
}
