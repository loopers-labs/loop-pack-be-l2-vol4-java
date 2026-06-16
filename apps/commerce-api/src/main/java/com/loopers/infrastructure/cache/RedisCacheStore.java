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

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        String cached = readSafe(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, type);
            } catch (JsonProcessingException e) {
                log.warn("cache deserialize 실패 key={}", key, e);
            }
        }
        T value = loader.get();
        writeSafe(key, value, ttl);
        return value;
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("cache evict 실패 key={}", key, e);
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
