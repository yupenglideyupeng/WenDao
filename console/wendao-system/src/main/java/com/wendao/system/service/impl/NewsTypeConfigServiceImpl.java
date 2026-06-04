package com.wendao.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wendao.system.domain.NewsTypeConfig;
import com.wendao.system.mapper.NewsTypeConfigMapper;
import com.wendao.system.service.INewsTypeConfigService;

@Service
public class NewsTypeConfigServiceImpl implements INewsTypeConfigService
{
    @Autowired
    private NewsTypeConfigMapper mapper;

    @Override public NewsTypeConfig selectById(Long id) { return mapper.selectById(id); }
    @Override public NewsTypeConfig selectByCode(String code) { return mapper.selectByCode(code); }
    @Override public List<NewsTypeConfig> selectList(NewsTypeConfig c) { return mapper.selectList(c); }
    @Override public List<NewsTypeConfig> selectActive() { return mapper.selectActive(); }
    @Override public int insert(NewsTypeConfig c) { return mapper.insert(c); }
    @Override public int update(NewsTypeConfig c) { return mapper.update(c); }
    @Override public int deleteByIds(Long[] ids) { return mapper.deleteByIds(ids); }
}
