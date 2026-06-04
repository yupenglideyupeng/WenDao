package com.wendao.system.service;

import java.util.List;
import com.wendao.system.domain.NewsTypeConfig;

public interface INewsTypeConfigService
{
    public NewsTypeConfig selectById(Long id);
    public NewsTypeConfig selectByCode(String typeCode);
    public List<NewsTypeConfig> selectList(NewsTypeConfig config);
    public List<NewsTypeConfig> selectActive();
    public int insert(NewsTypeConfig config);
    public int update(NewsTypeConfig config);
    public int deleteByIds(Long[] ids);
}
