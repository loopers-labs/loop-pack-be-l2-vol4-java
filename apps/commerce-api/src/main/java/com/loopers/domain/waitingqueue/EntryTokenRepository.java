package com.loopers.domain.waitingqueue;

/**
 * 입장 토큰·활성 세트 저장소(포트). 구현은 Redis(pass:{token}, user-pass:{userId}, active:users)
 * — docs/week8/04-redis-model.md §2.3~2.5.
 */
public interface EntryTokenRepository {

    /** 현재 활성(유효 토큰 보유)인가. active:users score > now 판정. */
    boolean isActive(Long userId);

    /** 만료 원소를 제외한 실제 활성 인원(관측용, 부수효과 없음 — score > now 카운트). */
    long activeCountLive();

    /** 토큰 → userId. 없거나 만료면 null. */
    Long findUserIdByToken(String token);

    /** userId → token(역참조). 없으면 null. */
    String findTokenByUser(Long userId);

    /** 토큰 소모(주문 성공 확정): pass/user-pass/active 모두 제거. */
    void consume(Long userId, String token);
}
