package com.soloeats.marketing.service;

import com.soloeats.marketing.service.view.RedPacketView;

import java.util.List;

/**
 * C 端红包。金额单位分，分配使用二倍均值法，全程 long。
 */
public interface RedPacketService {

    /** 当前可抢的红包列表；已领取的项 grabbed=true。 */
    List<RedPacketView> listAvailable(Long userId);

    /**
     * 抢红包。
     *
     * @return 本次抢到的金额，分；已领取过则返回原金额
     */
    Long grab(Long packetId, Long userId);
}
