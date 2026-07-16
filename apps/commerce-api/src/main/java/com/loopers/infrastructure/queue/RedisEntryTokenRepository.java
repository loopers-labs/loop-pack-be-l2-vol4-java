package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 입장 토큰 Redis 어댑터.
 * - key({@code entry-token:{userId}}) = 토큰 문자열, TTL 만료.
 * - master 템플릿: 발급 직후 검증(read-your-own-write) 일관성. (결정 #3)
 */
@Component
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisEntryTokenRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public EntryToken issue(Long userId, Duration ttl) {
        EntryToken issued = EntryToken.generate();
        redisTemplate.opsForValue().set(keyOf(userId), issued.value(), ttl);
        return issued;
    }

    @Override
    public Optional<EntryToken> find(Long userId) {
        String value = redisTemplate.opsForValue().get(keyOf(userId));
        return Optional.ofNullable(value).map(EntryToken::of);
    }

    @Override
    public void consume(Long userId) {
        redisTemplate.delete(keyOf(userId));
    }

    private String keyOf(Long userId) {
        return KEY_PREFIX + userId;
    }
}
