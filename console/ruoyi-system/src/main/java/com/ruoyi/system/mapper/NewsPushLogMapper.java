package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.NewsPushLog;

/**
 * 新闻推送记录 数据层
 *
 * @author ruoyi
 */
public interface NewsPushLogMapper
{
    /**
     * 查询推送记录
     */
    public NewsPushLog selectPushLogById(Long id);

    /**
     * 查询推送记录列表
     */
    public List<NewsPushLog> selectPushLogList(NewsPushLog log);

    /**
     * 新增推送记录
     */
    public int insertPushLog(NewsPushLog log);

    /**
     * 批量删除推送记录
     */
    public int deletePushLogByIds(Long[] ids);
}
