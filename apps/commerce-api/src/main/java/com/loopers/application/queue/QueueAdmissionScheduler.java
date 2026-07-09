package com.loopers.application.queue;

import com.loopers.domain.queue.QueueAdmissionRepository;
import com.loopers.infrastructure.queue.QueueProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

// 대기열에서 admitBatch 만큼 배치 발급하는 스케줄러. OutboxRelay 와 동일하게 개별 실패는 삼키고 다음 tick 에 재시도한다.
// (@EnableScheduling 은 CommerceApiApplication 에 이미 선언돼 있다.)
@Slf4j
@Component
public class QueueAdmissionScheduler {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final QueueAdmissionRepository queueAdmissionRepository;
    private final QueueProperties queueProperties;
    private final AtomicLong lastExecutionTimestamp = new AtomicLong();

    public QueueAdmissionScheduler(
            QueueAdmissionRepository queueAdmissionRepository,
            QueueProperties queueProperties,
            MeterRegistry meterRegistry
    ) {
        this.queueAdmissionRepository = queueAdmissionRepository;
        this.queueProperties = queueProperties;
        meterRegistry.gauge("queue.scheduler.last.execution.timestamp", lastExecutionTimestamp);
    }

    @Scheduled(fixedDelayString = "${queue.scheduler-interval-ms}")
    public void admit() {
        try {
            queueAdmissionRepository.admitBatch(queueProperties.batchSize(), TOKEN_TTL);
        } catch (Exception e) {
            log.error("대기열 배치 발급 실패", e);
        } finally {
            lastExecutionTimestamp.set(Instant.now().toEpochMilli());
        }
    }
}
