package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 기반 look-aside 캐시 저장소.
 *
 * <p>fail-open 정책: Redis 장애가 서비스 장애로 전파되지 않도록 모든 예외를 흡수한다. 조회 실패는 캐시 미스로 처리해 DB
 * 조회로 진행하고, 저장/무효화 실패는 무시한다 (TTL 로 최종 수렴).
 */
@Slf4j
@Component
public class RedisCacheRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheRepository(
        RedisTemplate<String, String> redisTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> find(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, type));
        } catch (Exception e) {
            log.warn("[cache] 캐시 조회 실패 — 미스로 처리합니다. key={}", key, e);
            return Optional.empty();
        }
    }

    public void save(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("[cache] 캐시 저장 실패 — 무시합니다. key={}", key, e);
        }
    }

    /** 무효화는 복제 지연의 영향을 받지 않도록 master 템플릿으로 수행한다. */
    public void evict(String key) {
        try {
            masterRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[cache] 캐시 무효화 실패 — 무시합니다. key={}", key, e);
        }
    }
}
