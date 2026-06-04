package com.wendao.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wendao.system.domain.NewsPushLog;
import com.wendao.system.mapper.NewsPushLogMapper;
import com.wendao.system.service.INewsPushLogService;

/**
 * 新闻推送记录 服务层实现
 *
 * @author wendao
 */
@Service
public class NewsPushLogServiceImpl implements INewsPushLogService
{
    @Autowired
    private NewsPushLogMapper pushLogMapper;

    @Override
    public NewsPushLog selectPushLogById(Long id)
    {
        return pushLogMapper.selectPushLogById(id);
    }

    @Override
    public List<NewsPushLog> selectPushLogList(NewsPushLog log)
    {
        return pushLogMapper.selectPushLogList(log);
    }

    @Override
    public int insertPushLog(NewsPushLog log)
    {
        return pushLogMapper.insertPushLog(log);
    }

    @Override
    public int deletePushLogByIds(Long[] ids)
    {
        return pushLogMapper.deletePushLogByIds(ids);
    }
}
