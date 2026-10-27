package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.SeckillActivity;
import com.soloeats.marketing.domain.entity.SeckillItem;
import com.soloeats.marketing.domain.entity.SeckillOrder;
import com.soloeats.marketing.mapper.SeckillActivityMapper;
import com.soloeats.marketing.mapper.SeckillItemMapper;
import com.soloeats.marketing.mapper.SeckillOrderMapper;
import com.soloeats.marketing.service.SeckillReserveResult;
import com.soloeats.marketing.service.SeckillService;
import com.soloeats.marketing.service.command.SeckillActivitySaveCommand;
import com.soloeats.marketing.service.view.SeckillActivityView;
import com.soloeats.marketing.service.view.SeckillSessionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 秒杀实现。
 *
 * <p><b>库存三段式</b>（对齐任务卡「秒杀不超卖」）：
 * <ol>
 *   <li>Redis {@code INCRBY} 原子预扣：{@code seckill:stock:{itemId}}，结果为负立即回补并抛 40203；</li>
 *   <li>DB 条件更新 {@code sold_qty + qty <= seckill_stock}，返回 0 行回补 Redis 并抛 40203；</li>
 *   <li>释放时 status 0 → 2、{@code sold_qty} 回补、Redis 回补，三步都以条件更新/原子操作幂等。</li>
 * </ol>
 *
 * <p><b>为什么 {@link #reserve} 不加 {@code @Transactional}</b>：失败补偿依赖「Redis 回补」与
 * 「DB 回补」各自独立提交。若整段包在事务里，捕获 {@link DuplicateKeyException} 后事务已被标记
 * rollback-only，随后的 {@code decreaseSoldQty} 不会生效，DB 会永久多扣库存。每条语句自动提交
 * 才能让补偿矩阵成立。
 *
 * <p><b>Redis 余量初始化</b>：key 不存在时用 {@code seckill_stock - sold_qty}（DB 当前余量）
 * {@code SETNX}，不能用 totalStock，否则服务重启会把已售库存重新加回去。
 *
 * <p><b>为什么用 {@link StringRedisTemplate} 而不是 Redisson {@code RAtomicLong}</b>：
 * 实测 redisson 3.52 的 {@code RAtomicLong} 没有 {@code setIfAbsent(long)}（只有 compareAndSet），
 * 无法表达「key 不存在才初始化」。{@code ValueOperations#setIfAbsent} + {@code increment}
 * 正好覆盖 SETNX / INCRBY 两个原子操作，且与 {@code RateLimitAspect} 使用同一客户端。
 * Redis 不可用时降级为只靠 DB 条件更新（业务不中断，防超卖不降级）。
 *
 * <p><b>状态口径</b>：DB 的 status 是写入时快照，读取时按 {@code session_start/session_end}
 * 与当前时间重新推导（{@link #effectiveStatus}），因此没有定时翻转任务也不会把进行中的场次
 * 判成「未开始」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String RESERVE_TOKEN_PREFIX = "SK";

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_ENDED = 2;

    private static final int ORDER_RESERVED = 0;
    private static final int ORDER_RELEASED = 2;

    private static final int NAME_MAX_LENGTH = 64;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final SeckillActivityMapper activityMapper;
    private final SeckillItemMapper itemMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;

    // ---------------------------------------------------------------
    // 后台场次维护
    // ---------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActivity(SeckillActivitySaveCommand command) {
        Normalized normalized = normalize(command);

        SeckillActivity activity = new SeckillActivity();
        activity.setActivityName(normalized.name());
        activity.setSessionStart(normalized.start());
        activity.setSessionEnd(normalized.end());
        activity.setPreheatAt(normalized.preheatAt());
        activity.setStatus(deriveStatus(normalized.start(), normalized.end(), LocalDateTime.now()));
        activity.setDeleted(0);
        activityMapper.insert(activity);

        Long activityId = activity.getId();
        if (activityId == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀场次创建失败，未生成主键");
        }
        for (SeckillActivitySaveCommand.ItemCommand item : normalized.items()) {
            itemMapper.insert(buildItem(activityId, item));
        }
        log.info("seckill activity created activityId={} name={} items={}",
                activityId, normalized.name(), normalized.items().size());
        return activityId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActivity(Long id, SeckillActivitySaveCommand command) {
        requireActivity(id);
        Normalized normalized = normalize(command);

        LambdaUpdateWrapper<SeckillActivity> wrapper = new LambdaUpdateWrapper<SeckillActivity>()
                .eq(SeckillActivity::getId, id)
                .eq(SeckillActivity::getDeleted, 0)
                .set(SeckillActivity::getActivityName, normalized.name())
                .set(SeckillActivity::getSessionStart, normalized.start())
                .set(SeckillActivity::getSessionEnd, normalized.end())
                .set(SeckillActivity::getPreheatAt, normalized.preheatAt())
                .set(SeckillActivity::getStatus,
                        deriveStatus(normalized.start(), normalized.end(), LocalDateTime.now()));
        if (activityMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "秒杀场次不存在或已删除");
        }

        syncItems(id, normalized.items());
        log.info("seckill activity updated activityId={} name={} items={}",
                id, normalized.name(), normalized.items().size());
    }

    @Override
    public PageResult<SeckillActivityView> pageActivities(Integer status, long pageNum, long pageSize) {
        if (status != null && (status < STATUS_PENDING || status > STATUS_ENDED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0待开始 / 1进行中 / 2已结束");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L
                ? DEFAULT_PAGE_SIZE
                : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getDeleted, 0)
                .eq(status != null, SeckillActivity::getStatus, status)
                .orderByDesc(SeckillActivity::getId);
        Page<SeckillActivity> page = activityMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);

        LocalDateTime now = LocalDateTime.now();
        // 列表不展开商品：最多 100 行，逐行查子表会放大成 100 次查询。
        List<SeckillActivityView> views = page.getRecords().stream()
                .map(activity -> SeckillActivityView.from(
                        activity, effectiveStatus(activity, now), List.of()))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public SeckillActivityView detailActivity(Long id) {
        SeckillActivity activity = requireActivity(id);
        return SeckillActivityView.from(
                activity,
                effectiveStatus(activity, LocalDateTime.now()),
                itemMapper.selectByActivityId(id));
    }

    @Override
    public List<SeckillSessionView> listSessions(LocalDateTime at) {
        LocalDateTime viewpoint = at == null ? LocalDateTime.now() : at;
        List<SeckillActivity> activities = activityMapper.selectDisplayableSessions(viewpoint);
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> activityIds = activities.stream().map(SeckillActivity::getId).toList();
        Map<Long, List<SeckillItem>> itemsByActivity = itemMapper.selectByActivityIds(activityIds)
                .stream()
                .collect(Collectors.groupingBy(
                        SeckillItem::getActivityId, LinkedHashMap::new, Collectors.toList()));

        return activities.stream()
                .map(activity -> SeckillSessionView.from(
                        activity,
                        effectiveStatus(activity, viewpoint),
                        itemsByActivity.getOrDefault(activity.getId(), List.of())))
                .toList();
    }

    // ---------------------------------------------------------------
    // C 端预占 / 释放
    // ---------------------------------------------------------------

    @Override
    public SeckillReserveResult reserve(Long itemId, Long userId, int qty) {
        if (itemId == null || itemId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "itemId 必须是正整数");
        }
        if (userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 不能为空");
        }
        if (qty < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "qty 必须大于等于 1");
        }

        SeckillItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "秒杀商品不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillActivity activity = item.getActivityId() == null
                ? null
                : activityMapper.selectById(item.getActivityId());
        if (activity == null || effectiveStatus(activity, now) != STATUS_ONGOING) {
            throw new BizException(ErrorCode.SECKILL_NOT_STARTED);
        }

        int limitPerUser = item.getLimitPerUser() == null ? 1 : item.getLimitPerUser();
        if (qty > limitPerUser) {
            throw new BizException(ErrorCode.SECKILL_LIMIT_EXCEEDED);
        }

        // 已预占/已支付记录直接拒绝；status=2 的旧记录可复用（uk_user_item 不允许重复 INSERT）
        SeckillOrder existing = orderMapper.selectByUserAndItem(userId, itemId);
        boolean reuseReleased = existing != null
                && Objects.equals(existing.getStatus(), ORDER_RELEASED);
        if (existing != null && !reuseReleased) {
            throw new BizException(ErrorCode.SECKILL_LIMIT_EXCEEDED, "每人限购 " + limitPerUser + " 件，已预占");
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        int totalStock = item.getSeckillStock() == null ? 0 : item.getSeckillStock();
        int soldQty = item.getSoldQty() == null ? 0 : item.getSoldQty();

        // null 表示 Redis 不可用或未扣减：此时只靠 DB 条件更新，失败路径也不回补 Redis
        Long redisRemain = deductRedis(stockKey, Math.max(totalStock - soldQty, 0), qty);
        if (redisRemain != null && redisRemain < 0) {
            rollbackRedisQuietly(stockKey, qty, true);
            throw new BizException(ErrorCode.SECKILL_OUT_OF_STOCK);
        }

        int updated;
        try {
            updated = itemMapper.increaseSoldQty(itemId, qty);
        } catch (RuntimeException ex) {
            // DB 未写入，只需回补 Redis
            rollbackRedisQuietly(stockKey, qty, redisRemain != null);
            throw ex;
        }
        if (updated == 0) {
            // DB 余量不足：Redis 预扣成功但 DB 拒绝了，回补 Redis 后按售罄返回
            rollbackRedisQuietly(stockKey, qty, redisRemain != null);
            throw new BizException(ErrorCode.SECKILL_OUT_OF_STOCK);
        }

        Long orderId = reuseReleased
                ? reactivateReleasedOrder(existing, itemId, qty, stockKey, redisRemain != null)
                : insertReservedOrder(item, userId, qty, itemId, stockKey, redisRemain != null);

        long price = item.getSeckillPrice() == null ? 0L : item.getSeckillPrice();
        log.info("seckill reserved itemId={} userId={} qty={} orderId={} redisRemain={} reused={}",
                itemId, userId, qty, orderId, redisRemain, reuseReleased);
        return new SeckillReserveResult(
                orderId, itemId, qty, price, RESERVE_TOKEN_PREFIX + orderId);
    }

    @Override
    public void release(Long itemId, Long userId) {
        if (itemId == null || itemId <= 0L || userId == null || userId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "itemId 与 userId 必须是正整数");
        }

        SeckillOrder order = orderMapper.selectByUserAndItem(userId, itemId);
        if (order == null || !Objects.equals(order.getStatus(), ORDER_RESERVED)) {
            // 不存在或已释放：幂等返回，重复调用不报错
            log.debug("seckill release skipped itemId={} userId={} status={}",
                    itemId, userId, order == null ? null : order.getStatus());
            return;
        }

        if (orderMapper.release(order.getId()) == 0) {
            // 并发已释放
            return;
        }

        int qty = order.getQty() == null || order.getQty() < 1 ? 1 : order.getQty();
        if (itemMapper.decreaseSoldQty(itemId, qty) == 0) {
            log.warn("seckill release db inconsistent itemId={} qty={}（sold_qty 可能已被其他链路回补）",
                    itemId, qty);
        }
        String stockKey = STOCK_KEY_PREFIX + itemId;
        try {
            // key 不存在说明余量从未初始化（或已过期淘汰），无需 INCR，避免凭空造出库存
            if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                redisTemplate.opsForValue().increment(stockKey, qty);
            }
        } catch (RuntimeException ex) {
            log.warn("seckill release redis rollback failed itemId={} qty={}", itemId, qty, ex);
        }
        log.info("seckill released itemId={} userId={} orderId={} qty={}",
                itemId, userId, order.getId(), qty);
    }

    // ---------------------------------------------------------------
    // 失败补偿
    // ---------------------------------------------------------------

    /**
     * 预占失败回补：DB 与 Redis 同时回补，两边各自 try/catch，避免二次异常掩盖原始错误。
     * redisDeducted=false 表示 Redis 预扣未发生（不可用），只回补 DB。
     */
    private void rollbackStock(Long itemId, int qty, String stockKey, boolean redisDeducted) {
        try {
            if (itemMapper.decreaseSoldQty(itemId, qty) == 0) {
                log.warn("seckill rollback db no rows itemId={} qty={}", itemId, qty);
            }
        } catch (RuntimeException ex) {
            log.error("seckill rollback db failed itemId={} qty={}", itemId, qty, ex);
        }
        rollbackRedisQuietly(stockKey, qty, redisDeducted);
    }

    private void rollbackRedisQuietly(String stockKey, int qty, boolean redisDeducted) {
        if (!redisDeducted) {
            return;
        }
        try {
            redisTemplate.opsForValue().increment(stockKey, qty);
        } catch (RuntimeException ex) {
            log.error("seckill rollback redis failed key={} qty={}（以 DB 为准等待对账）",
                    stockKey, qty, ex);
        }
    }

    /**
     * Redis 预扣：SETNX 用 DB 当前余量初始化，再 INCRBY 原子扣减。
     *
     * @return 扣减后的余量；Redis 不可用时返回 null，调用方降级为只靠 DB 条件更新
     */
    private Long deductRedis(String stockKey, int dbRemaining, int qty) {
        try {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            ops.setIfAbsent(stockKey, String.valueOf(dbRemaining));
            return ops.increment(stockKey, -qty);
        } catch (RuntimeException ex) {
            log.warn("seckill redis unavailable, fallback to db guard key={} qty={}",
                    stockKey, qty, ex);
            return null;
        }
    }

    // ---------------------------------------------------------------
    // 预占单落库
    // ---------------------------------------------------------------

    /**
     * 已释放的旧记录：UPDATE 回到 status=0，不 INSERT（uk_user_item 会撞键）。
     */
    private Long reactivateReleasedOrder(SeckillOrder existing, Long itemId, int qty,
                                         String stockKey, boolean redisDeducted) {
        Long orderId = existing.getId();
        int rows;
        try {
            rows = orderMapper.reactivate(orderId, qty);
        } catch (DuplicateKeyException ex) {
            rollbackStock(itemId, qty, stockKey, redisDeducted);
            throw new BizException(ErrorCode.SECKILL_LIMIT_EXCEEDED);
        } catch (RuntimeException ex) {
            rollbackStock(itemId, qty, stockKey, redisDeducted);
            throw ex;
        }
        if (rows == 0) {
            rollbackStock(itemId, qty, stockKey, redisDeducted);
            throw new BizException(ErrorCode.SECKILL_LIMIT_EXCEEDED, "该商品已被重复预占");
        }
        return orderId;
    }

    private Long insertReservedOrder(SeckillItem item, Long userId, int qty,
                                     Long itemId, String stockKey, boolean redisDeducted) {
        SeckillOrder order = new SeckillOrder();
        order.setActivityId(item.getActivityId());
        order.setItemId(itemId);
        order.setUserId(userId);
        order.setQty(qty);
        order.setStatus(ORDER_RESERVED);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            rollbackStock(itemId, qty, stockKey, redisDeducted);
            throw new BizException(ErrorCode.SECKILL_LIMIT_EXCEEDED);
        } catch (RuntimeException ex) {
            rollbackStock(itemId, qty, stockKey, redisDeducted);
            throw ex;
        }
        if (order.getId() == null) {
            rollbackStock(itemId, qty, stockKey, redisDeducted);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀预占单写入失败，未生成主键");
        }
        return order.getId();
    }

    // ---------------------------------------------------------------
    // 子表同步
    // ---------------------------------------------------------------

    /**
     * 按 skuId 对齐商品：已存在则更新价格/库存/限购（库存不得低于 sold_qty）；
     * 新增则插入；移除仅允许 sold_qty=0，否则拒绝，防止 t_seckill_order 指向已删除商品。
     */
    private void syncItems(Long activityId, List<SeckillActivitySaveCommand.ItemCommand> commands) {
        List<SeckillItem> existingItems = itemMapper.selectByActivityId(activityId);
        Map<Long, SeckillItem> bySkuId = existingItems.stream()
                .collect(Collectors.toMap(
                        SeckillItem::getSkuId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<Long> keptSkuIds = new HashSet<>();

        for (SeckillActivitySaveCommand.ItemCommand command : commands) {
            keptSkuIds.add(command.skuId());
            SeckillItem current = bySkuId.get(command.skuId());
            if (current == null) {
                itemMapper.insert(buildItem(activityId, command));
                continue;
            }
            int soldQty = current.getSoldQty() == null ? 0 : current.getSoldQty();
            if (command.seckillStock() < soldQty) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "skuId=" + command.skuId() + " 的 seckillStock 不能小于已售/已预占数量 " + soldQty);
            }
            itemMapper.update(null, new LambdaUpdateWrapper<SeckillItem>()
                    .eq(SeckillItem::getId, current.getId())
                    .set(SeckillItem::getShopId, command.shopId())
                    .set(SeckillItem::getSpuId, command.spuId())
                    .set(SeckillItem::getSeckillPrice, command.seckillPrice())
                    .set(SeckillItem::getSeckillStock, command.seckillStock())
                    .set(SeckillItem::getLimitPerUser, command.limitPerUser()));
        }

        for (SeckillItem item : existingItems) {
            if (keptSkuIds.contains(item.getSkuId())) {
                continue;
            }
            int soldQty = item.getSoldQty() == null ? 0 : item.getSoldQty();
            if (soldQty > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "skuId=" + item.getSkuId() + " 已产生预占，不能从场次移除");
            }
            itemMapper.deleteById(item.getId());
        }
    }

    // ---------------------------------------------------------------
    // 校验与工具
    // ---------------------------------------------------------------

    private Normalized normalize(SeckillActivitySaveCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        String name = StringUtils.hasText(command.activityName())
                ? command.activityName().trim()
                : null;
        if (name == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "activityName 不能为空");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "activityName 不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }

        LocalDateTime start = command.sessionStart();
        LocalDateTime end = command.sessionEnd();
        if (start == null || end == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "sessionStart 与 sessionEnd 不能为空");
        }
        if (!start.isBefore(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "sessionStart 必须早于 sessionEnd");
        }

        if (command.items() == null || command.items().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "items 不能为空");
        }
        Set<Long> skuIds = new HashSet<>();
        List<SeckillActivitySaveCommand.ItemCommand> items = new ArrayList<>(command.items().size());
        for (SeckillActivitySaveCommand.ItemCommand item : command.items()) {
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "items 不能包含空项");
            }
            requirePositiveId(item.shopId(), "shopId");
            requirePositiveId(item.spuId(), "spuId");
            requirePositiveId(item.skuId(), "skuId");
            if (item.seckillPrice() == null || item.seckillPrice() <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "seckillPrice 必须大于 0");
            }
            if (item.seckillStock() == null || item.seckillStock() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "seckillStock 必须大于 0");
            }
            if (item.limitPerUser() == null || item.limitPerUser() < 1) {
                throw new BizException(ErrorCode.PARAM_INVALID, "limitPerUser 必须大于等于 1");
            }
            if (!skuIds.add(item.skuId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "items 中的 skuId 不能重复：" + item.skuId());
            }
            items.add(item);
        }
        return new Normalized(name, start, end, command.preheatAt(), List.copyOf(items));
    }

    private static void requirePositiveId(Long id, String field) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须是正整数");
        }
    }

    private SeckillItem buildItem(Long activityId, SeckillActivitySaveCommand.ItemCommand command) {
        SeckillItem item = new SeckillItem();
        item.setActivityId(activityId);
        item.setShopId(command.shopId());
        item.setSpuId(command.spuId());
        item.setSkuId(command.skuId());
        item.setSeckillPrice(command.seckillPrice());
        item.setSeckillStock(command.seckillStock());
        item.setSoldQty(0);
        item.setLimitPerUser(command.limitPerUser());
        item.setVersion(0);
        return item;
    }

    private SeckillActivity requireActivity(Long id) {
        SeckillActivity activity = id == null ? null : activityMapper.selectById(id);
        if (activity == null || Objects.equals(activity.getDeleted(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "秒杀场次不存在");
        }
        return activity;
    }

    /**
     * 写入时快照状态。
     */
    private static int deriveStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (now.isBefore(start)) {
            return STATUS_PENDING;
        }
        if (now.isAfter(end)) {
            return STATUS_ENDED;
        }
        return STATUS_ONGOING;
    }

    /**
     * 读取时的业务状态：DB 的 status 只用来尊重已结束（2）这一终态，
     * 其余按时间窗实时推导，避免没有定时任务时进行中的场次仍显示「待开始」。
     */
    private static int effectiveStatus(SeckillActivity activity, LocalDateTime now) {
        if (activity.getSessionStart() == null || activity.getSessionEnd() == null) {
            return STATUS_ENDED;
        }
        if (Objects.equals(activity.getStatus(), STATUS_ENDED)) {
            return STATUS_ENDED;
        }
        return deriveStatus(activity.getSessionStart(), activity.getSessionEnd(), now);
    }

    private record Normalized(
            String name,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime preheatAt,
            List<SeckillActivitySaveCommand.ItemCommand> items
    ) {
    }
}
