package com.loopers.infrastructure.order;

import com.loopers.application.order.IdempotencyManager;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyManager implements IdempotencyManager {

    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final String SUCCESS_PREFIX = "idempotency:success:";

    @Override
    public Long getSuccess(String idempotencyKey) {
        String val = defaultRedisTemplate.opsForValue().get(SUCCESS_PREFIX + idempotencyKey);
        return val != null ? Long.parseLong(val) : null;
    }

    @Override
    public boolean lock(String idempotencyKey) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + idempotencyKey);
        try {
            // waitTime = 0: 즉시 락 획득 시도 (대기 안함)
            // leaseTime = -1: Redisson Watchdog 활성화 (자동 갱신)
            return lock.tryLock(0, -1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String idempotencyKey) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + idempotencyKey);
        // 토큰(Thread ID)이 일치하고 현재 락이 걸려있을 때만 해제
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public void saveSuccess(String idempotencyKey, Long orderId) {
        defaultRedisTemplate.opsForValue()
                .set(SUCCESS_PREFIX + idempotencyKey, String.valueOf(orderId), Duration.ofHours(24));
    }

    @Override
    public void savePayloadHash(String idempotencyKey, String payloadHash) {
        defaultRedisTemplate.opsForValue()
                .set("idempotency:hash:" + idempotencyKey, payloadHash, Duration.ofHours(24));
    }

    @Override
    public String getPayloadHash(String idempotencyKey) {
        return defaultRedisTemplate.opsForValue().get("idempotency:hash:" + idempotencyKey);
    }
}
