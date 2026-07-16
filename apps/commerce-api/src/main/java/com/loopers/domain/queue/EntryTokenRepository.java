package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * 입장 토큰. 대기열을 통과한 유저에게 발급되는 주문 API 진입 권한으로,
 * TTL 내에 사용하지 않으면 자동 만료된다(이탈 유저가 자리를 계속 점유하지 않도록).
 */
public interface EntryTokenRepository {

    /** 유저에게 토큰을 발급한다. TTL 경과 시 자동 소멸. */
    void issue(Long userId, String token, Duration ttl);

    /** 유저의 유효한(미만료) 토큰. 없으면 empty. */
    Optional<String> find(Long userId);

    /** 토큰을 제거한다(주문 완료 후 소진 처리). */
    void delete(Long userId);

    /**
     * 유저당 in-flight 주문 1건만 통과시키는 배타 마크를 선점한다(SETNX). 이미 처리 중이면 {@code false}.
     * 토큰 소진이 주문 완료 후에 일어나 검사~소진 사이에 같은 토큰의 동시 주문이 겹칠 수 있으므로,
     * 이 마크가 그 창을 닫는다. TTL 은 정상 해제({@link #releaseInFlight})가 크래시로 누락됐을 때의
     * 자동 해제 안전망이다(주문 처리 최대 시간보다 넉넉히 크게).
     *
     * <p>{@code ownerToken}(요청마다 고유)을 값으로 저장한다 — TTL 이 만료돼 다른 요청이 새로 선점한
     * 뒤 늦게 도착한 해제가 남의 마크를 지우지 않도록, 해제는 이 토큰이 일치할 때만 수행한다.</p>
     */
    boolean acquireInFlight(Long userId, String ownerToken, Duration ttl);

    /**
     * in-flight 마크를 해제한다(주문 처리 완료 — 성공·실패 무관). 저장된 값이 {@code ownerToken} 과
     * 일치할 때만 삭제한다(compare-and-delete) — 만료 후 새 소유자가 선점한 마크를 지우지 않기 위함.
     */
    void releaseInFlight(Long userId, String ownerToken);
}
