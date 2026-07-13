package com.loopers.queue.infrastructure;

import com.loopers.queue.domain.AdmissionLock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisAdmissionLock implements AdmissionLock {

    private static final String KEY = "order-queue:admission-lock";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean tryAcquire(Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }
}
