package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class EntryTokenRepositoryImpl implements EntryTokenRepository {

    private final RedisTemplate<String, String> redisTemplate;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> masterRedisTemplate;

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(entryTokenKey(userId)));
    }

    @Override
    public void delete(Long userId) {
        masterRedisTemplate.delete(entryTokenKey(userId));
    }

    private String entryTokenKey(Long userId) {
        return QueueRedisKeys.ENTRY_TOKEN_KEY_PREFIX + userId;
    }
}
