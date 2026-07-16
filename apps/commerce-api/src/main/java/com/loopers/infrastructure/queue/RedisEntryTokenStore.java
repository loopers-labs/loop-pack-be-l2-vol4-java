package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenStore;
import com.loopers.support.config.QueueProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RedisEntryTokenStore implements EntryTokenStore {  // 입장 토큰 저장소의 Redis String+TTL 어댑터. 발급 직후 검증하므로 master 템플릿 사용.

    private static final String KEY_PREFIX = "queue:token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueProperties queueProperties;

    public RedisEntryTokenStore(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            QueueProperties queueProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.queueProperties = queueProperties;
    }

    @Override
    public String issue(String loginId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(loginId), token, queueProperties.token().ttl());
        return token;
    }

    @Override
    public Optional<String> find(String loginId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(loginId)));
    }

    @Override
    public void delete(String loginId) {
        redisTemplate.delete(key(loginId));
    }

    private static String key(String loginId) {
        return KEY_PREFIX + loginId;
    }
}