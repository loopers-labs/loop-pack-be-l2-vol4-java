package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public final class QueueV1Dto {

    private QueueV1Dto() {
    }

    /**
     * 대기 중: {@code position ≥ 1} + 예상 대기·다음 폴링 간격 ({@code token=null}) /
     * 입장 가능: {@code position = 0} + {@code token} ({@code pollAfterMillis=null}).
     * {@code totalWaiting} 은 조회 시점의 전체 대기 인원("N명이 함께 대기 중").
     * 예상 대기 시간·전체 대기 인원은 추정값이므로 클라이언트는 "약 N초"·"약 N명"으로 표기한다.
     */
    public record PositionResponse(
            long position,
            long estimatedWaitSeconds,
            Long pollAfterMillis,
            String token,
            long totalWaiting
    ) {

        public static PositionResponse from(QueueInfo.Position info) {
            return new PositionResponse(
                    info.position(),
                    info.estimatedWaitSeconds(),
                    info.pollAfterMillis(),
                    info.token(),
                    info.totalWaiting()
            );
        }
    }
}
