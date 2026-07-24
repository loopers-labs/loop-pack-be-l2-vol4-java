package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ValueOperations<String, String> ops;

    public RedisEntryTokenRepository(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.ops = redisTemplate.opsForValue();
    }

    @Override
    public void save(Long userId, String token, Duration ttl) {
        // SET entry-token:{userId} {token} EX ttl : TTL 만료 시 Redis가 키를 자동 삭제한다.
        ops.set(key(userId), token, ttl);
    }

    @Override
    public Optional<String> find(Long userId) {
        // GET : 만료됐거나 없으면 null → empty.
        return Optional.ofNullable(ops.get(key(userId)));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
