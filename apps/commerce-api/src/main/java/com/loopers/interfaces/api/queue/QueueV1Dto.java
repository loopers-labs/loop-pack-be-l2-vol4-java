package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    /**
     * 대기열 상태 응답 — 입장 완료(토큰 발급)면 position 0 + token, 대기 중이면 token 은 null(직렬화 시 생략).
     * suggestedPollIntervalSeconds: 클라이언트가 다음 순번 조회까지 기다릴 권장 간격(초). 입장 완료면 0.
     */
    public record QueueResponse(
        long position,
        long waitingCount,
        long estimatedWaitSeconds,
        long suggestedPollIntervalSeconds,
        String token
    ) {
        public static QueueResponse from(QueueInfo info) {
            return new QueueResponse(
                info.position(),
                info.waitingCount(),
                info.estimatedWaitSeconds(),
                info.suggestedPollIntervalSeconds(),
                info.token()
            );
        }
    }
}
