package com.loopers.infrastructure.ranking.lock;

import com.loopers.domain.ranking.batch.RankingBatchLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * SET NX PX(setIfAbsent)로 락을 획득하고, 저장된 값이 자신의 token과 같을 때만 삭제하는
 * Lua 스크립트로 해제한다(compare-and-delete). TTL이 있어 락 소유자가 unlock 없이 죽어도
 * (JVM 강제 종료 등) 일정 시간 후 자동 해제되어 영구 잠금을 방지한다.
 */
@Component
public class RedisRankingBatchLock implements RankingBatchLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        else
            return 0
        end
        """,
        Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingBatchLock(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, String token, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String key, String token) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
    }
}