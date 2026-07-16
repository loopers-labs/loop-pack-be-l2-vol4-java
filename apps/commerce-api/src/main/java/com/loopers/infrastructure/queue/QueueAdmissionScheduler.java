package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueService;
import com.loopers.support.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입장 스케줄러. 주기(interval-ms)마다 대기열 앞에서 batch-size 만큼 꺼내 입장 토큰을 발급한다.
 * 1초 175명을 한 번에 발급하지 않고 100ms×18명으로 잘게 나눠 Thundering Herd 를 완화한다.
 * 테스트에서는 대기열 상태를 통제하기 위해 queue.admission.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "queue.admission", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QueueAdmissionScheduler {

    private final QueueService queueService;
    private final QueueProperties queueProperties;

    @Scheduled(fixedDelayString = "${queue.admission.interval-ms:100}")
    public void admit() {
        queueService.admitNext(queueProperties.admission().batchSize());
    }
}