package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * 입장 토큰 seam 인터페이스. 구현은 infrastructure(Redis STRING + TTL) 에 둔다(DIP).
 * 토큰은 1회용 — 주문 성공 시 삭제되고, TTL 만료 시 재진입해야 한다.
 * (저장소 동사는 delete — "소진(consume)"이라는 비즈니스 동사는 EntryTokenGate 가 가진다.)
 * 유저 식별자는 loginId(String) — 이 코드베이스에서 userId 는 Long PK 를 뜻하므로 혼용하지 않는다.
 */
public interface EntryTokenRepository {

    /** 입장 토큰 발급(SET EX). 같은 유저에 재발급하면 덮어쓴다. */
    void issue(String loginId, String token, Duration ttl);

    /** 유효한(미만료) 토큰 조회(GET). 없거나 만료면 empty. */
    Optional<String> find(String loginId);

    /** 토큰 삭제(DEL). */
    void delete(String loginId);
}
