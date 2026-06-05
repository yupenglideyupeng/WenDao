package com.wendao.system.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wendao.system.domain.NewsInterpretation;
import com.wendao.system.mapper.NewsInterpretationMapper;
import com.wendao.system.service.INewsInterpretationService;

import java.util.List;

/**
 * 新闻解读记录 服务层实现
 *
 * @author wendao
 */
@Service
public class NewsInterpretationServiceImpl implements INewsInterpretationService
{
    @Autowired
    private NewsInterpretationMapper interpretationMapper;

    @Override
    public NewsInterpretation selectLatestByArticleId(Long articleId)
    {
        return interpretationMapper.selectLatestByArticleId(articleId);
    }

    @Override
    public List<NewsInterpretation> selectListByArticleId(Long articleId)
    {
        return interpretationMapper.selectListByArticleId(articleId);
    }

    @Override
    public int insert(NewsInterpretation interpretation)
    {
        return interpretationMapper.insert(interpretation);
    }

    @Override
    public int update(NewsInterpretation interpretation)
    {
        return interpretationMapper.update(interpretation);
    }
}
