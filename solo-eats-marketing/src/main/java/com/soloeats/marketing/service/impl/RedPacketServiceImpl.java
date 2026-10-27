package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.RedPacket;
import com.soloeats.marketing.domain.entity.UserRedPacket;
import com.soloeats.marketing.mapper.RedPacketMapper;
import com.soloeats.marketing.mapper.UserRedPacketMapper;
import com.soloeats.marketing.service.RedPacketService;
import com.soloeats.marketing.service.view.RedPacketView;
import com.soloeats.marketing.support.RedPacketSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 拼手气红包。金额全程 long 分，二倍均值法由 {@link RedPacketSplitter} 实现。
 *
 * <p>为什么不用纯条件更新：二倍均值法必须先读「剩余金额 + 剩余份数」才能算出本次金额，
 * 单条 UPDATE 无法同时完成读取与计算。因此 grab 在事务内先对红包主表行
 * {@code SELECT ... FOR UPDATE}，把「读剩余 → 算金额 → 条件扣减 → 写用户红包」
 * 串行化；入参校验在加锁前完成，锁只覆盖这一段。行锁也弥补了
 * t_user_red_packet 没有 (packet_id, user_id) 唯一键的问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedPacketServiceImpl implements RedPacketService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int STATUS_UNUSED = 0;
    private static final int STATUS_ENDED = 2;
    private static final int STATUS_DISABLED = 3;

    private final RedPacketMapper redPacketMapper;
    private final UserRedPacketMapper userRedPacketMapper;

    @Override
    public List<RedPacketView> listAvailable(Long userId) {
        requireUserId(userId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<RedPacket> packets = redPacketMapper.selectAvailable(now);

        List<Long> grabbedIds = userRedPacketMapper.selectPacketIdsByUser(userId);
        Set<Long> grabbed = grabbedIds == null ? Set.of() : new HashSet<>(grabbedIds);

        return packets.stream()
                .map(packet -> RedPacketView.from(packet, grabbed.contains(packet.getId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grab(Long packetId, Long userId) {
        // 入参校验放在加锁前，锁只包住下方的读-算-扣-写
        if (packetId == null || packetId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "packetId 必须是正整数");
        }
        requireUserId(userId);

        RedPacket packet = redPacketMapper.selectForUpdate(packetId);
        if (packet == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "红包不存在");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (Integer.valueOf(STATUS_ENDED).equals(packet.getStatus())
                || Integer.valueOf(STATUS_DISABLED).equals(packet.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "红包已结束或已停用");
        }
        if (packet.getStartAt() == null || packet.getEndAt() == null
                || now.isBefore(packet.getStartAt())
                || !now.isBefore(packet.getEndAt())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "红包不在可领取时间");
        }
        if (packet.getRemainQty() == null || packet.getRemainQty() <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "红包已抢完");
        }

        UserRedPacket existing = userRedPacketMapper.selectByPacketAndUser(packetId, userId);
        if (existing != null) {
            return existing.getAmount();
        }

        long totalAmount = packet.getTotalAmount() == null ? 0L : packet.getTotalAmount();
        int remainQty = packet.getRemainQty();
        long issuedAmount = userRedPacketMapper.sumAmountByPacket(packetId);
        long remainingAmount = totalAmount - issuedAmount;
        if (remainingAmount < remainQty) {
            log.error("red packet amount inconsistent packetId={} total={} issued={} remainQty={}",
                    packetId, totalAmount, issuedAmount, remainQty);
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "红包金额数据异常");
        }

        long amount = RedPacketSplitter.draw(
                remainingAmount, remainQty, ThreadLocalRandom.current());
        if (redPacketMapper.reserveOne(packetId) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "红包已抢完");
        }

        UserRedPacket record = new UserRedPacket();
        record.setPacketId(packetId);
        record.setUserId(userId);
        record.setAmount(amount);
        record.setStatus(STATUS_UNUSED);
        record.setExpireAt(packet.getEndAt());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        if (userRedPacketMapper.insert(record) != 1) {
            // 抛异常让事务回滚，恢复 remain_qty
            throw new BizException(ErrorCode.SYSTEM_ERROR, "红包领取失败");
        }

        log.info("red packet grabbed packetId={} userId={} amount={} remainQtyAfter={}",
                packetId, userId, amount, remainQty - 1);
        return amount;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 必须是正整数");
        }
    }
}
