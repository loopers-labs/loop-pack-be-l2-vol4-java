package com.loopers.support.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> find(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("캐시 조회 실패 - key={}, 미스로 폴백", key, e);

            return Optional.empty();
        }
    }

    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> optionalCacheData = find(key, type);

        if (optionalCacheData.isPresent()) {
            return optionalCacheData.get();
        }

        T loadedData = loader.get();
        put(key, loadedData, ttl);

        return loadedData;
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("캐시 저장 실패 - key={}, 저장 생략", key, e);
        }
    }

    public void evictAfterCommit(String key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("트랜잭션 커밋 후 캐시 무효화 시작 - key={}", key);
                    evict(key);
                }
            });

            return;
        }

        evict(key);
    }

    private void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("캐시 무효화 실패 - key={}", key, e);
        }
    }
}
