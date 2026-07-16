package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.QueueStatus;
import com.loopers.domain.queue.QueueStatusService;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WaitingQueueFacade {

    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueStatusService queueStatusService;
    private final double admissionThroughputPerSecond;

    public WaitingQueueFacade(
        WaitingQueueRepository waitingQueueRepository,
        QueueStatusService queueStatusService,
        @Value("${queue.admission.batch-size}") int batchSize,
        @Value("${queue.admission.interval}") Duration interval
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.queueStatusService = queueStatusService;
        // 예상 대기의 처리량은 스케줄러와 같은 config(batch-size/interval)에서 유도 → 추정치가 실제 입장 속도와 드리프트하지 않음.
        this.admissionThroughputPerSecond = batchSize / (interval.toMillis() / 1000.0);
    }

    /** 대기열 진입: 진입시키고 현재 순번과 전체 대기 인원을 돌려준다. 이미 대기 중이면 순번을 유지한다. */
    public QueueInfo enter(Long userId) {
        QueuePosition position = waitingQueueRepository.enroll(userId);
        return QueueInfo.of(position, waitingQueueRepository.size());
    }

    /** 순번 조회: 유저 상태(대기 중/입장됨)에 따라 순번·예상대기, 또는 토큰을 돌려준다. */
    public QueuePositionInfo getPosition(Long userId) {
        QueueStatus status = queueStatusService.statusOf(userId);
        return QueuePositionInfo.from(status, admissionThroughputPerSecond);
    }
}
