package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.NewsPushLog;
import com.ruoyi.system.mapper.NewsPushLogMapper;
import com.ruoyi.system.service.INewsPushLogService;

/**
 * 新闻推送记录 服务层实现
 *
 * @author ruoyi
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
