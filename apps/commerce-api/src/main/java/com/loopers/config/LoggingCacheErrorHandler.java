package com.loopers.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Redis 장애 시 캐시 예외를 삼키고 DB 로 폴백되게 한다.
 * (캐시는 "성능 보조"이지 "필수 경로"가 아니므로, 캐시가 죽어도 서비스는 살아야 한다.)
 */
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
        log.warn("[Cache] GET 실패 → DB 폴백. cache={}, key={}", cache.getName(), key, ex);
    }

    @Override
    public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
        log.warn("[Cache] PUT 실패. cache={}, key={}", cache.getName(), key, ex);
    }

    @Override
    public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
        log.warn("[Cache] EVICT 실패. cache={}, key={}", cache.getName(), key, ex);
    }

    @Override
    public void handleCacheClearError(RuntimeException ex, Cache cache) {
        log.warn("[Cache] CLEAR 실패. cache={}", cache.getName(), ex);
    }
}
