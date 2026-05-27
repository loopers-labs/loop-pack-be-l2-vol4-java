package com.loopers.infrastructure.event.scheduler;

import com.loopers.application.event.relay.EventRelayWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventRelayWorkerSchedulerTest {

    @DisplayName("스케줄러가 실행되면 pending outbox relay worker에 처리를 위임한다.")
    @Test
    void delegatesToEventRelayWorker_whenScheduledMethodRuns() {
        // arrange
        EventRelayWorker eventRelayWorker = mock(EventRelayWorker.class);
        when(eventRelayWorker.relayPendingEvents()).thenReturn(List.of());
        EventRelayWorkerScheduler scheduler = new EventRelayWorkerScheduler(eventRelayWorker);

        // act
        scheduler.relayPendingEvents();

        // assert
        verify(eventRelayWorker).relayPendingEvents();
    }
}
