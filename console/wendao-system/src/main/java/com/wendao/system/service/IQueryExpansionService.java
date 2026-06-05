package com.wendao.system.service;

import java.util.List;

/**
 * 查询扩展 服务层接口
 *
 * @author wendao
 */
public interface IQueryExpansionService
{
    /**
     * 扩展关键词搜索词列表（含原始词）
     * @param keyword 原始关键词
     * @param keywordId 关键词ID（用于缓存关联）
     * @return 搜索词列表，包含原始词和扩展词（总共2-4个）
     */
    List<String> expand(String keyword, Long keywordId);
}
