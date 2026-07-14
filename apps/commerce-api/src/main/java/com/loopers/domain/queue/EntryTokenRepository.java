package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * 대기열 통과 후 발급되는 입장 토큰 저장소. TTL이 지나면 자동 만료된다.
 */
public interface EntryTokenRepository {

    /** 새 토큰을 발급하고 TTL을 설정한다. */
    String issue(Long userId, Duration ttl);

    /** 해당 유저의 유효한(만료되지 않은) 토큰. 없으면 empty. */
    Optional<String> find(Long userId);

    /** 해당 유저의 유효한(만료되지 않은) 토큰과 일치하는지 검증한다. */
    boolean isValid(Long userId, String token);

    /** 토큰을 삭제한다. */
    void delete(Long userId);
}
