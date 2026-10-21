package com.soloeats.common.lock;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁封装。
 * 锁只用于跨行编排，业务状态仍以数据库条件更新为最终防线。
 */
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final RedissonClient redissonClient;

    public <T> T executeWithLock(String key, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BizException(ErrorCode.LOCK_TIMEOUT);
            }
            return action.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.LOCK_TIMEOUT);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void executeWithLock(String key, long waitSeconds, long leaseSeconds, Runnable action) {
        executeWithLock(key, waitSeconds, leaseSeconds, () -> {
            action.run();
            return null;
        });
    }
}
