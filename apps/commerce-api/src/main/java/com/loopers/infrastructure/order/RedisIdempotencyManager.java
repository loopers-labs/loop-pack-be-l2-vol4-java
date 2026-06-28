package com.loopers.infrastructure.order;

import com.loopers.application.order.IdempotencyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyManager implements IdempotencyManager {

    private final RedisTemplate<String, String> defaultRedisTemplate;

    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final String SUCCESS_PREFIX = "idempotency:success:";

    @Override
    public Long getSuccess(String idempotencyKey) {
        String val = defaultRedisTemplate.opsForValue().get(SUCCESS_PREFIX + idempotencyKey);
        return val != null ? Long.parseLong(val) : null;
    }

    @Override
    public boolean lock(String idempotencyKey) {
        Boolean success = defaultRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + idempotencyKey, "LOCKED", Duration.ofSeconds(10));
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock(String idempotencyKey) {
        defaultRedisTemplate.delete(LOCK_PREFIX + idempotencyKey);
    }

    @Override
    public void saveSuccess(String idempotencyKey, Long orderId) {
        defaultRedisTemplate.opsForValue()
                .set(SUCCESS_PREFIX + idempotencyKey, String.valueOf(orderId), Duration.ofHours(24));
    }
}
