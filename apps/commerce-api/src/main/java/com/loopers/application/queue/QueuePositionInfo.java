package com.loopers.application.queue;

import com.loopers.domain.queue.EstimatedWait;
import com.loopers.domain.queue.QueueStatus;

/**
 * 순번 조회 결과. 대기 중이면 순번·전체 대기 인원·예상 대기(문구)를, 입장됐으면 순번 0·"곧 주문 가능"·토큰을 담는다.
 * {@code token} 은 입장(내 차례) 시에만 존재하며 대기 중에는 null.
 */
public record QueuePositionInfo(long position, long totalWaiting, String estimatedWait, String token) {

    /** 도메인 상태(sealed)를 순번 응답 형태로 매핑한다. throughput 은 예상 대기 산정용 초당 처리량. */
    public static QueuePositionInfo from(QueueStatus status, double throughputPerSecond) {
        return switch (status) {
            case QueueStatus.Waiting w -> new QueuePositionInfo(
                w.position().value(),
                w.totalWaiting(),
                EstimatedWait.of(w.position().value(), throughputPerSecond).description(),
                null
            );
            case QueueStatus.Admitted a -> new QueuePositionInfo(
                0L,
                0L,
                EstimatedWait.of(0L, throughputPerSecond).description(),
                a.token().value()
            );
        };
    }
}
