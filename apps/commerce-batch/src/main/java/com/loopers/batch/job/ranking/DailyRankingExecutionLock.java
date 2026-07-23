package com.loopers.batch.job.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Component
public class DailyRankingExecutionLock {
    private static final Duration LOCK_TTL = Duration.ofHours(2);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        end
        return 0
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public DailyRankingExecutionLock(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(LocalDate requestDate, long jobExecutionId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            lockKey(requestDate),
            token(jobExecutionId),
            LOCK_TTL
        );
        return Boolean.TRUE.equals(acquired);
    }

    public boolean release(LocalDate requestDate, long jobExecutionId) {
        Long released = redisTemplate.execute(
            RELEASE_SCRIPT,
            List.of(lockKey(requestDate)),
            token(jobExecutionId)
        );
        return Long.valueOf(1L).equals(released);
    }

    private String lockKey(LocalDate requestDate) {
        return DailyRankingKey.from(requestDate) + ":snapshot-lock";
    }

    private String token(long jobExecutionId) {
        return Long.toString(jobExecutionId);
    }
}
