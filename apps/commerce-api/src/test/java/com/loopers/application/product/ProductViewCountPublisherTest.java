package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.activity.UserActivityEvent;
import com.loopers.application.outbox.OutboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductViewCountPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ProductViewCountPublisher publisher = new ProductViewCountPublisher(kafkaTemplate, new ObjectMapper());

    @DisplayName("상품 조회(PRODUCT_VIEWED) 이벤트를 받으면 catalog-events 로 VIEWED 메시지를 발행한다.")
    @Test
    void publishesViewedToCatalogEvents_whenProductViewed() {
        // given
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // when
        publisher.on(UserActivityEvent.of(null, UserActivityEvent.Type.PRODUCT_VIEWED, 42L));

        // then
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("catalog-events"), eq("42"), valueCaptor.capture());
        OutboxMessage message = (OutboxMessage) valueCaptor.getValue();
        assertAll(
            () -> assertThat(message.eventType()).isEqualTo("VIEWED"),
            () -> assertThat(message.aggregateId()).isEqualTo(42L)
        );
    }

    @DisplayName("조회가 아닌 다른 행동(PRODUCT_LIKED) 이벤트에는 조회수 메시지를 발행하지 않는다.")
    @Test
    void doesNotPublish_whenActivityIsNotView() {
        // when
        publisher.on(UserActivityEvent.of(1L, UserActivityEvent.Type.PRODUCT_LIKED, 42L));

        // then
        verify(kafkaTemplate, never()).send(anyString(), any(), any());
    }
}
