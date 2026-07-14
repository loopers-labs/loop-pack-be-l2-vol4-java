package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis String 기반 입장 토큰 저장소. key {@code entry-token:{userId}}, value = 토큰, TTL 만료 시 자동 삭제.
 */
@Repository
public class EntryTokenRedisRepository implements EntryTokenRepository {

    private static final String KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issue(Long userId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(userId), token, ttl);
        return token;
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public boolean isValid(Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && stored.equals(token);
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
