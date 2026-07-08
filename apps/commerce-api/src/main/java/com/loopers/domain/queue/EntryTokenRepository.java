package com.loopers.domain.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface EntryTokenRepository {

    /**
     * 대기열 앞에서 count 명을 꺼내(ZPOPMIN) 각자에게 입장 토큰을 발급한다.
     * 꺼내기와 토큰 발급은 원자적으로 수행되며, 실제 발급된 userId 목록을 반환한다.
     * (대기 인원이 count 보다 적으면 그만큼만 발급된다)
     */
    List<Long> issueToNext(int count, Duration ttl);

    /** 유저의 입장 토큰. 없으면 empty. */
    Optional<String> find(Long userId);
}
