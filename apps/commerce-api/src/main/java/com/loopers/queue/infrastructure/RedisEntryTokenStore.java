package com.loopers.queue.infrastructure;

import com.loopers.queue.domain.EntryTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisEntryTokenStore implements EntryTokenStore {

    private static final String KEY = "order-queue:entry-token:%s";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public String issue(String userId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(userId), token, ttl);
        return token;
    }

    @Override
    public Optional<String> find(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void remove(String userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(String userId) {
        return KEY.formatted(userId);
    }
}
