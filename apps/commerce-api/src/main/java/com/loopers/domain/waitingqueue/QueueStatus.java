package com.loopers.domain.waitingqueue;

/**
 * 대기열 상태 머신(사용자 관점). docs/week8/03-class-diagram.md §4 상태 전이 참조.
 */
public enum QueueStatus {
    /** 대기 중. rank/eta 유효. */
    WAITING,
    /** 입장 토큰 발급됨 → 주문 API로 진행. 폴링 중단 신호. */
    READY,
    /** 대기열에 없음(미진입/취소/소모 완료). */
    NOT_IN_QUEUE,
    /** 토큰이 만료됨(재진입 필요). */
    EXPIRED
}
