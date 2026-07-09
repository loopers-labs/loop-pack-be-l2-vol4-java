package com.loopers.tddstudy.infrastructure.queue;

import com.loopers.tddstudy.domain.queue.EntryTokenRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
public class EntryTokenRedisRepository implements EntryTokenRepository {

    private static final String PREFIX = "entry-token:";
    // 검증(GET==token)과 삭제(DEL)를 한 덩어리로 — 토큰 재사용 방지
    private static final String CONSUME_LUA = """
        local saved = redis.call('GET', KEYS[1])
        if saved == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 1
        else
            return 0
        end
        """;

    private final StringRedisTemplate redis;

    public EntryTokenRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void issue(Long userId, String token, long ttlSeconds) {
        redis.opsForValue().set(PREFIX + userId, token, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public String find(Long userId) {
        return redis.opsForValue().get(PREFIX + userId);
    }

    @Override
    public boolean consume(Long userId, String token) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CONSUME_LUA, Long.class);
        Long r = redis.execute(script, List.of(PREFIX + userId), token);
        return r != null && r == 1L;
    }
}
