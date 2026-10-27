package com.soloeats.report.service;

import com.soloeats.report.service.command.TrackEventCommand;

import java.util.List;

/**
 * 埋点写入。单次最多 20 条，逐条落 t_track_event。
 */
public interface TrackService {

    /**
     * @param userId 登录消费者 ID；匿名上报传 null
     * @param events 事件列表，1~20 条
     * @return 实际写入行数
     */
    int track(Long userId, List<TrackEventCommand> events);
}
