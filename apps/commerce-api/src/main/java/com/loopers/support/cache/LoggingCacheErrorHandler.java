package com.loopers.support.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * 캐시 레이어 오류를 로그로 남기고 삼킨다(rethrow 하지 않음).
 *
 * <p>get 오류를 삼키면 Spring 은 캐시 미스로 간주해 원본 메서드(DB)를 실행한다 → Redis 장애 시 DB 폴백.
 * evict/clear 오류를 삼켜도 TTL 만료가 stale 을 self-heal 한다.
 *
 * <p>비즈니스 예외(메서드 본문에서 발생)는 건드리지 않으므로 "의도 없는 광범위 catch" 가 아니라
 * 캐시 인프라에 국한된 의도적 처리이며, 모든 실패를 로그로 남겨 침묵 swallow 도 아니다.
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("[cache] get 실패 → DB 폴백. cache={}, key={}", cache.getName(), key, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("[cache] put 실패(캐시 미적재). cache={}, key={}", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("[cache] evict 실패(TTL self-heal 의존). cache={}, key={}", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("[cache] clear 실패. cache={}", cache.getName(), exception);
    }
}
