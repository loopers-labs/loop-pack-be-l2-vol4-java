package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;

/**
 * 대기열 조회 결과. 내 순번(0-based)과 전체 대기 인원.
 */
public record QueueInfo(long position, long totalWaiting) {

    public static QueueInfo of(QueuePosition position, long totalWaiting) {
        return new QueueInfo(position.value(), totalWaiting);
    }
}
