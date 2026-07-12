package com.loopers.infrastructure.ordering.queue;

import com.loopers.application.ordering.queue.OrderQueueAdmissionWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueueAdmissionWorkerSchedulerTest {

    @DisplayName("스케줄러가 실행되면 주문 대기열 입장 worker에 처리를 위임한다.")
    @Test
    void delegatesToOrderQueueAdmissionWorker_whenScheduledMethodRuns() {
        // arrange
        OrderQueueAdmissionWorker worker = mock(OrderQueueAdmissionWorker.class);
        when(worker.admitNext()).thenReturn(List.of());
        OrderQueueAdmissionWorkerScheduler scheduler = new OrderQueueAdmissionWorkerScheduler(worker);

        // act
        scheduler.admitWaitingUsers();

        // assert
        verify(worker).admitNext();
    }
}
