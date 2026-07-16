package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenStore {  // 입장 토큰(주문 진입 권한) 저장소 포트. TTL 만료로 미사용 토큰이 자동 회수된다.

    /** 새 토큰을 발급·저장(TTL)하고 토큰 값을 반환한다. */
    String issue(String loginId);

    /** 저장된 토큰. 없거나 만료됐으면 비어 있음. */
    Optional<String> find(String loginId);

    /** 토큰 삭제(주문 완료 후 소비). */
    void delete(String loginId);
}