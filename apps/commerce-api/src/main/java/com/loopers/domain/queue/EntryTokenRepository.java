package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * 입장 토큰 port. 토큰의 발급·조회·삭제와 TTL 만료를 캡슐화한다. (구현은 Redis 어댑터)
 */
public interface EntryTokenRepository {

    /** 입장 토큰을 발급하고 ttl 뒤 자동 만료되도록 저장한다. 발급한 토큰을 반환. */
    EntryToken issue(Long userId, Duration ttl);

    /** 유저의 유효한(미만료) 입장 토큰 조회. 없거나 만료됐으면 빈 Optional. */
    Optional<EntryToken> find(Long userId);

    /** 사용 완료된 토큰 삭제. */
    void consume(Long userId);
}
