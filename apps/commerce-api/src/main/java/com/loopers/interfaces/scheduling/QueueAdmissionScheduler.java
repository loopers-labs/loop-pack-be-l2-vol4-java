package com.loopers.interfaces.scheduling;

import com.loopers.domain.queue.AdmissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입장(Admission) 스케줄러 — queue.admission.enabled=true 인 프로파일(dev/qa/prd)에서만 등록된다.
 * local/test 는 미설정 → 스케줄러 미등록 → 통제 불가한 동시 액터가 ZPOPMIN 으로 큐 상태를 바꿔 테스트가 flaky 해지는 것 방지. (결정 #7)
 * 입장 처리 로직 자체는 {@code AdmissionService} 빈으로 항상 존재 → 테스트는 admit() 을 직접 호출한다.
 * batch-size(~18) 산정 근거는 ADR-003.
 */
@Component
@ConditionalOnProperty(name = "queue.admission.enabled", havingValue = "true")
public class QueueAdmissionScheduler {

    private final AdmissionService admissionService;
    private final int batchSize;

    public QueueAdmissionScheduler(
        AdmissionService admissionService,
        @Value("${queue.admission.batch-size}") int batchSize
    ) {
        this.admissionService = admissionService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${queue.admission.interval}")
    public void admit() {
        admissionService.admit(batchSize);
    }
}
