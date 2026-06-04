package com.wendao.system.service;

import java.util.List;
import com.wendao.system.domain.NewsKeyword;

/**
 * 新闻关键词 服务层
 *
 * @author wendao
 */
public interface INewsKeywordService
{
    public NewsKeyword selectKeywordById(Long id);

    public List<NewsKeyword> selectKeywordList(NewsKeyword keyword);

    public List<NewsKeyword> selectActiveKeywords();

    public int insertKeyword(NewsKeyword keyword);

    public int updateKeyword(NewsKeyword keyword);

    public int deleteKeywordByIds(Long[] ids);
}
