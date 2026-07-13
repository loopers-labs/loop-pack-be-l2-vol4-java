package com.loopers.domain.ratelimit;

/**
 * Redis 기반 고정 윈도우(fixed-window) 카운터 저장소.
 *
 * <p>애플리케이션 인스턴스가 여러 대로 스케일아웃되면 JVM 로컬 카운터로는 전체 요청량을
 * 정확히 셀 수 없다 — 인스턴스마다 별도로 세면 실제 한도의 N배까지 허용되어 버린다.
 * Redis에 카운터를 두면 인스턴스 수와 무관하게 하나의 진실(source of truth)로 카운트된다.
 */
public interface RateLimitRedisStore {

    /**
     * 지정한 키에 대해 windowSeconds 동안 최대 limit 회까지 허용한다(고정 윈도우 방식).
     *
     * @return 이번 호출이 한도 이내면 true, 한도를 초과하면 false
     */
    boolean tryAcquire(String key, int limit, int windowSeconds);
}
