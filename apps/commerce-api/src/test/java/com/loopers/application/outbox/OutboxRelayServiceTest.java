package com.loopers.application.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayServiceTest {

    @DisplayName("outbox 이벤트 발행이 성공하면 PUBLISHED 상태로 저장한다.")
    @Test
    void marksPublished_whenPublishSucceeds() {
        // arrange
        OutboxEvent event = new OutboxEvent("event-1", "catalog-events", "1", "PRODUCT_LIKED", "{}");
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
        when(repository.findRelayableEvents(10)).thenReturn(List.of(event));
        OutboxRelayService relayService = new OutboxRelayService(
            repository,
            publisher,
            new OutboxProperties(true, Duration.ofSeconds(5), 10, Duration.ofSeconds(3)),
            new SimpleMeterRegistry()
        );

        // act
        relayService.relayReadyEvents();

        // assert
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(publisher).publish(event);
        verify(repository).save(event);
    }

    @DisplayName("outbox 이벤트 발행이 실패하면 FAILED 상태와 실패 원인을 저장한다.")
    @Test
    void marksFailed_whenPublishFails() {
        // arrange
        OutboxEvent event = new OutboxEvent("event-1", "catalog-events", "1", "PRODUCT_LIKED", "{}");
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
        when(repository.findRelayableEvents(10)).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new IllegalStateException("kafka down"))
            .when(publisher)
            .publish(event);
        OutboxRelayService relayService = new OutboxRelayService(
            repository,
            publisher,
            new OutboxProperties(true, Duration.ofSeconds(5), 10, Duration.ofSeconds(3)),
            new SimpleMeterRegistry()
        );

        // act
        relayService.relayReadyEvents();

        // assert
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getFailReason()).isEqualTo("IllegalStateException");
        verify(repository).save(event);
    }
}
