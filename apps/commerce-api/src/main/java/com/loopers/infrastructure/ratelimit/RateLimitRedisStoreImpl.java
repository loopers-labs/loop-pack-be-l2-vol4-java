package com.loopers.infrastructure.ratelimit;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ratelimit.RateLimitRedisStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RateLimitRedisStoreImpl implements RateLimitRedisStore {

    private static final Long ALLOWED = 1L;

    /** INCR 후 카운트가 1이면(윈도우의 첫 호출) TTL을 건다 — 그 외엔 기존 TTL을 유지한 채 카운트만 늘린다. */
    private static final DefaultRedisScript<Long> TRY_ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
        local key = KEYS[1]
        local windowSeconds = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])

        local count = redis.call('INCR', key)
        if count == 1 then
            redis.call('EXPIRE', key, windowSeconds)
        end
        if count > limit then
            return 0
        end
        return 1
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitRedisStoreImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long result = redisTemplate.execute(
            TRY_ACQUIRE_SCRIPT,
            List.of(windowKey(key, windowSeconds)),
            Integer.toString(windowSeconds),
            Integer.toString(limit)
        );
        return ALLOWED.equals(result);
    }

    /** 현재 윈도우 구간(epoch초 / windowSeconds)을 키에 붙여, 윈도우가 바뀌면 자연히 새 카운터로 넘어가게 한다. */
    private String windowKey(String key, int windowSeconds) {
        long bucket = System.currentTimeMillis() / 1000 / windowSeconds;
        return key + ":" + bucket;
    }
}
