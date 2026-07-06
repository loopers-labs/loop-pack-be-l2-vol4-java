package com.loopers.queue.infrastructure;

import com.loopers.queue.application.OrderQueueAdmissionProperties;
import com.loopers.queue.application.QueueService;
import com.loopers.queue.domain.AdmissionLock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 주기적으로 대기열에서 입장 토큰을 발급한다. 여러 인스턴스여도 락을 잡은 한 대만 발급한다.
 * test 프로파일에서는 비활성화한다 — 백그라운드 발급이 큐 테스트의 상태를 흔들지 않게.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AdmissionScheduler {

    private final AdmissionLock admissionLock;
    private final QueueService queueService;
    private final OrderQueueAdmissionProperties admissionProperties;

    @Scheduled(fixedDelayString = "${order-queue.admission.interval-ms}")
    public void tick() {
        Duration lockTtl = Duration.ofMillis(admissionProperties.intervalMs());
        if (admissionLock.tryAcquire(lockTtl)) {
            queueService.admit();
        }
    }
}
