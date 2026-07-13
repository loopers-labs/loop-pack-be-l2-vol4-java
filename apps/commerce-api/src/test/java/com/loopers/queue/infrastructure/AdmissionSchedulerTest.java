package com.loopers.queue.infrastructure;

import com.loopers.queue.application.OrderQueueAdmissionProperties;
import com.loopers.queue.application.QueueService;
import com.loopers.queue.domain.AdmissionLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionSchedulerTest {

    private final AdmissionLock admissionLock = mock(AdmissionLock.class);
    private final QueueService queueService = mock(QueueService.class);
    private final OrderQueueAdmissionProperties admissionProperties = new OrderQueueAdmissionProperties(200L, 35);
    private final AdmissionScheduler sut = new AdmissionScheduler(admissionLock, queueService, admissionProperties);

    @Test
    @DisplayName("락을 획득하면 발급을 실행한다")
    void givenLockAcquired_whenTick_thenAdmits() {
        when(admissionLock.tryAcquire(any())).thenReturn(true);

        sut.tick();

        verify(queueService).admit();
    }

    @Test
    @DisplayName("락을 못 잡으면 발급하지 않는다")
    void givenLockNotAcquired_whenTick_thenDoesNotAdmit() {
        when(admissionLock.tryAcquire(any())).thenReturn(false);

        sut.tick();

        verify(queueService, never()).admit();
    }
}
