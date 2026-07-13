package com.loopers.queue.domain;

import java.time.Duration;
import java.util.Optional;

public interface EntryTokenStore {

    /** 입장 토큰을 발급(생성 후 TTL과 함께 저장)하고 토큰 값을 돌려준다. */
    String issue(String userId, Duration ttl);

    /** 저장된 토큰. 없거나 만료됐으면 비어 있다. */
    Optional<String> find(String userId);

    /** 토큰을 삭제한다(주문 완료 후). */
    void remove(String userId);
}
