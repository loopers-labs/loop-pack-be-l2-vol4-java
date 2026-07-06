package com.loopers.outbox.application;

import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.domain.OutboxEventRepository;
import com.loopers.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventRelayTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
    private final OutboxEventRelay relay = new OutboxEventRelay(repository, publisher, 100);

    private OutboxEvent pending(String key, String payload) {
        return OutboxEvent.pending("order-events", key, "OrderCreated", payload);
    }

    @Test
    @DisplayName("PENDING 이벤트를 토픽으로 발행하고 PUBLISHED 로 표시한다")
    void givenPendingEvent_whenRelay_thenPublishesAndMarksPublished() {
        OutboxEvent event = pending("1", "{\"orderId\":1}");
        when(repository.findPendingForUpdate(anyInt())).thenReturn(List.of(event));

        relay.relay();

        verify(publisher).publish("order-events", "1", "{\"orderId\":1}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("발행에 실패하면 해당 이벤트는 PENDING 으로 남겨 다음 폴에서 재시도한다")
    void givenPublishFails_whenRelay_thenKeepsPending() {
        OutboxEvent event = pending("1", "{\"orderId\":1}");
        when(repository.findPendingForUpdate(anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("kafka down")).when(publisher).publish(anyString(), anyString(), anyString());

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("한 이벤트 발행이 실패해도 나머지 이벤트는 계속 발행한다")
    void givenOneFails_whenRelay_thenStillPublishesOthers() {
        OutboxEvent failing = pending("1", "{\"orderId\":1}");
        OutboxEvent ok = pending("2", "{\"orderId\":2}");
        when(repository.findPendingForUpdate(anyInt())).thenReturn(List.of(failing, ok));
        doThrow(new RuntimeException("kafka down"))
                .when(publisher).publish("order-events", "1", "{\"orderId\":1}");

        relay.relay();

        verify(publisher, times(1)).publish("order-events", "2", "{\"orderId\":2}");
        assertThat(failing.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(ok.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }
}
