package com.loopers.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class RedisCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("[Cache GET 실패] cache={}, key={} - DB로 fallback", cache.getName(), key, e);
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("[Cache PUT 실패] cache={}, key={} - 캐싱 생략", cache.getName(), key, e);
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("[Cache EVICT 실패] cache={}, key={} - stale 데이터 TTL 만료 전까지 잔존 가능", cache.getName(), key, e);
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("[Cache CLEAR 실패] cache={} - stale 데이터 TTL 만료 전까지 잔존 가능", cache.getName(), e);
    }
}
