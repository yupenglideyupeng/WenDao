package com.wendao.system.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型可用性状态（供前端查询用）
 */
public class ModelStatus
{
    /** 总模型数 */
    private int totalModels;
    /** 已启用模型数 */
    private int activeModels;
    /** 各场景是否可用 */
    private Map<String, Boolean> scenes;
    /** 各场景当前使用的模型名称 */
    private Map<String, String> modelNames;

    public ModelStatus()
    {
        this.scenes = new HashMap<>();
        this.modelNames = new HashMap<>();
    }

    public int getTotalModels() { return totalModels; }
    public void setTotalModels(int totalModels) { this.totalModels = totalModels; }

    public int getActiveModels() { return activeModels; }
    public void setActiveModels(int activeModels) { this.activeModels = activeModels; }

    public Map<String, Boolean> getScenes() { return scenes; }
    public void setScenes(Map<String, Boolean> scenes) { this.scenes = scenes; }

    public Map<String, String> getModelNames() { return modelNames; }
    public void setModelNames(Map<String, String> modelNames) { this.modelNames = modelNames; }

    /** 是否有任何可用模型 */
    public boolean isAnyAvailable()
    {
        return scenes.values().stream().anyMatch(Boolean::booleanValue);
    }

    /** 指定场景是否可用 */
    public boolean isSceneAvailable(String scene)
    {
        return Boolean.TRUE.equals(scenes.get(scene));
    }
}
