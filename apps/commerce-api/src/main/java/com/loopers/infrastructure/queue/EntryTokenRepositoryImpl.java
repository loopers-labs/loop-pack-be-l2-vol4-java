package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class EntryTokenRepositoryImpl implements EntryTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";
    private static final long TTL_SECONDS = 300;

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRepositoryImpl(@Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(userId), token, TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}
