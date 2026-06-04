package com.ruoyi.web.controller.news;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.NewsTypeConfig;
import com.ruoyi.system.service.INewsTypeConfigService;

@RestController
@RequestMapping("/news/typeConfig")
public class NewsTypeConfigController extends BaseController
{
    @Autowired
    private INewsTypeConfigService service;

    @PreAuthorize("@ss.hasPermi('news:type:list')")
    @Log(title = "新闻类型", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/list")
    public TableDataInfo list(NewsTypeConfig config) { startPage(); return getDataTable(service.selectList(config)); }

    @PreAuthorize("@ss.hasPermi('news:type:query')")
    @Log(title = "新闻类型", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) { return success(service.selectById(id)); }

    @PreAuthorize("@ss.hasPermi('news:type:add')")
    @Log(title = "新闻类型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody NewsTypeConfig config) { config.setCreateBy(getUsername()); return toAjax(service.insert(config)); }

    @PreAuthorize("@ss.hasPermi('news:type:edit')")
    @Log(title = "新闻类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody NewsTypeConfig config) { config.setUpdateBy(getUsername()); return toAjax(service.update(config)); }

    @PreAuthorize("@ss.hasPermi('news:type:remove')")
    @Log(title = "新闻类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) { return toAjax(service.deleteByIds(ids)); }
}
