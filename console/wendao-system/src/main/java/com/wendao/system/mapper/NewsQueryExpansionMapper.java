package com.wendao.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.wendao.system.domain.NewsQueryExpansion;

/**
 * 查询扩展词 数据层
 *
 * @author wendao
 */
public interface NewsQueryExpansionMapper
{
    /**
     * 根据关键词ID查询活跃的扩展词
     */
    public List<NewsQueryExpansion> selectByKeywordId(Long keywordId);

    /**
     * 新增扩展词
     */
    public int insertExpansion(NewsQueryExpansion expansion);

    /**
     * 根据关键词ID删除所有扩展词
     */
    public int deleteByKeywordId(Long keywordId);
}
