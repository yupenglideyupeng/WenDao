package com.wendao.web.controller.news;

import com.wendao.common.annotation.Log;
import com.wendao.common.core.controller.BaseController;
import com.wendao.common.core.domain.AjaxResult;
import com.wendao.common.core.page.TableDataInfo;
import com.wendao.common.enums.BusinessType;
import com.wendao.system.domain.NewsModelConfig;
import com.wendao.system.service.INewsModelConfigService;
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

import java.util.List;
import java.util.Map;

/**
 * AI模型配置管理
 *
 * @author wendao
 */
@RestController
@RequestMapping("/news/model")
public class NewsModelConfigController extends BaseController
{
    @Autowired
    private INewsModelConfigService modelConfigService;

    /**
     * 分页列表
     */
    @PreAuthorize("@ss.hasPermi('news:model:list')")
    @GetMapping("/list")
    public TableDataInfo list(NewsModelConfig config)
    {
        startPage();
        List<NewsModelConfig> list = modelConfigService.selectList(config);
        return getDataTable(list);
    }

    /**
     * 详情
     */
    @PreAuthorize("@ss.hasPermi('news:model:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(modelConfigService.selectById(id));
    }

    /**
     * 新增
     */
    @PreAuthorize("@ss.hasPermi('news:model:add')")
    @Log(title = "模型管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody NewsModelConfig config)
    {
        config.setCreateBy(getUsername());
        return toAjax(modelConfigService.insert(config));
    }

    /**
     * 修改
     */
    @PreAuthorize("@ss.hasPermi('news:model:edit')")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody NewsModelConfig config)
    {
        config.setUpdateBy(getUsername());
        return toAjax(modelConfigService.update(config));
    }

    /**
     * 批量删除
     */
    @PreAuthorize("@ss.hasPermi('news:model:remove')")
    @Log(title = "模型管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(modelConfigService.deleteByIds(ids));
    }

    /**
     * 测试模型连接
     */
    @PreAuthorize("@ss.hasPermi('news:model:query')")
    @Log(title = "模型管理-测试连接", businessType = BusinessType.OTHER)
    @PostMapping("/test/{id}")
    public AjaxResult testConnection(@PathVariable Long id)
    {
        Map<String, Object> result = modelConfigService.testConnection(id);
        return success(result);
    }
}
