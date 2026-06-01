package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.NewsPushLog;

/**
 * 新闻推送记录 服务层
 *
 * @author ruoyi
 */
public interface INewsPushLogService
{
    public NewsPushLog selectPushLogById(Long id);

    public List<NewsPushLog> selectPushLogList(NewsPushLog log);

    public int insertPushLog(NewsPushLog log);

    public int deletePushLogByIds(Long[] ids);
}
