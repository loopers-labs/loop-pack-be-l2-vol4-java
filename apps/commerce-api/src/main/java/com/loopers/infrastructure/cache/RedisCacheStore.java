package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.cache.CacheStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedisCacheStore implements CacheStore {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheStore.class);

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int LOAD_WAIT_TRIES = 20;
    private static final long LOAD_WAIT_MILLIS = 50L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T hit = read(key, type);
        if (hit != null) {
            return hit;
        }

        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent("lock:" + key, "1", LOCK_TTL);
        } catch (RuntimeException e) {
            log.warn("cache lock 실패 key={}", key, e);
            return loader.get();
        }

        if (Boolean.TRUE.equals(acquired)) {
            T value = loader.get();
            writeSafe(key, value, ttl);
            return value;
        }
        return awaitOrLoad(key, type, loader);
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("cache evict 실패 key={}", key, e);
        }
    }

    private <T> T awaitOrLoad(String key, Class<T> type, Supplier<T> loader) {
        for (int i = 0; i < LOAD_WAIT_TRIES; i++) {
            try {
                Thread.sleep(LOAD_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            T filled = read(key, type);
            if (filled != null) {
                return filled;
            }
        }
        return loader.get();
    }

    private <T> T read(String key, Class<T> type) {
        String cached = readSafe(key);
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, type);
        } catch (JsonProcessingException e) {
            log.warn("cache deserialize 실패 key={}", key, e);
            return null;
        }
    }

    private String readSafe(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("cache read 실패 key={}", key, e);
            return null;
        }
    }

    private <T> void writeSafe(String key, T value, Duration ttl) {
        if (value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("cache write 실패 key={}", key, e);
        }
    }
}
