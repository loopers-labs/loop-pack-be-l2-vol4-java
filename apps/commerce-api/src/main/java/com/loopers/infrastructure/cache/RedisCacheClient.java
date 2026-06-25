package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.cache.CacheClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisCacheClient implements CacheClient {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheClient.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Optional<T> find(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, type));
        } catch (Exception e) {
            // 캐시 조회 실패 시 캐시를 건너뛰고 DB 로 폴백한다.
            log.warn("캐시 조회 실패 - key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            // 캐시 저장 실패는 조회 동작에 영향을 주지 않도록 무시한다.
            log.warn("캐시 저장 실패 - key={}", key, e);
        }
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("캐시 무효화 실패 - key={}", key, e);
        }
    }
}