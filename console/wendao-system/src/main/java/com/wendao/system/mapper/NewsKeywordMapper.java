package com.wendao.system.mapper;

import java.util.List;
import com.wendao.system.domain.NewsKeyword;

/**
 * 新闻关键词 数据层
 *
 * @author wendao
 */
public interface NewsKeywordMapper
{
    public NewsKeyword selectKeywordById(Long id);

    public List<NewsKeyword> selectKeywordList(NewsKeyword keyword);

    public List<NewsKeyword> selectActiveKeywords();

    public int insertKeyword(NewsKeyword keyword);

    public int updateKeyword(NewsKeyword keyword);

    public int updateLastFetchTime(Long id);

    public int deleteKeywordById(Long id);

    public int deleteKeywordByIds(Long[] ids);
}
