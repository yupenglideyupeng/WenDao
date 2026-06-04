package com.wendao.web.controller.news;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.wendao.common.annotation.Log;
import com.wendao.common.core.controller.BaseController;
import com.wendao.common.core.domain.AjaxResult;
import com.wendao.common.core.page.TableDataInfo;
import com.wendao.common.enums.BusinessType;
import com.wendao.system.domain.NewsPromptConfig;
import com.wendao.system.service.INewsPromptConfigService;

@RestController
@RequestMapping("/news/promptConfig")
public class NewsPromptConfigController extends BaseController
{
    @Autowired
    private INewsPromptConfigService service;

    @PreAuthorize("@ss.hasPermi('news:prompt:list')")
    @Log(title = "提示词配置", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/list")
    public TableDataInfo list(NewsPromptConfig config) { startPage(); return getDataTable(service.selectList(config)); }

    @PreAuthorize("@ss.hasPermi('news:prompt:query')")
    @Log(title = "提示词配置", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) { return success(service.selectById(id)); }

    @PreAuthorize("@ss.hasPermi('news:prompt:add')")
    @Log(title = "提示词配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody NewsPromptConfig config) { config.setCreateBy(getUsername()); return toAjax(service.insert(config)); }

    @PreAuthorize("@ss.hasPermi('news:prompt:edit')")
    @Log(title = "提示词配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody NewsPromptConfig config) { config.setUpdateBy(getUsername()); return toAjax(service.update(config)); }

    @PreAuthorize("@ss.hasPermi('news:prompt:remove')")
    @Log(title = "提示词配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) { return toAjax(service.deleteByIds(ids)); }
}
