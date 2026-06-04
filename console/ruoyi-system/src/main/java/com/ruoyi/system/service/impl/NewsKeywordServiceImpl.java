package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.NewsKeyword;
import com.ruoyi.system.mapper.NewsKeywordMapper;
import com.ruoyi.system.service.INewsKeywordService;

/**
 * 新闻关键词 服务层实现
 *
 * @author ruoyi
 */
@Service
public class NewsKeywordServiceImpl implements INewsKeywordService
{
    @Autowired
    private NewsKeywordMapper keywordMapper;

    @Override
    public NewsKeyword selectKeywordById(Long id)
    {
        return keywordMapper.selectKeywordById(id);
    }

    @Override
    public List<NewsKeyword> selectKeywordList(NewsKeyword keyword)
    {
        return keywordMapper.selectKeywordList(keyword);
    }

    @Override
    public List<NewsKeyword> selectActiveKeywords()
    {
        return keywordMapper.selectActiveKeywords();
    }

    @Override
    public int insertKeyword(NewsKeyword keyword)
    {
        return keywordMapper.insertKeyword(keyword);
    }

    @Override
    public int updateKeyword(NewsKeyword keyword)
    {
        return keywordMapper.updateKeyword(keyword);
    }

    @Override
    public int deleteKeywordByIds(Long[] ids)
    {
        return keywordMapper.deleteKeywordByIds(ids);
    }
}
