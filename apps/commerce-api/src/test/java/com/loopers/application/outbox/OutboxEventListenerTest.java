package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.product.event.ProductViewed;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventListenerTest {

    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxEventListener listener = new OutboxEventListener(outboxRepository, objectMapper);

    @DisplayName("LikeAdded → catalog-events outbox row(key=productId, payload에 count/version).")
    @Test
    void writesCatalogOutboxOnLikeAdded() {
        listener.on(new LikeAdded(7L, 100L, 5L, 3L, ZonedDateTime.now()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        assertThat(row.getTopic()).isEqualTo("catalog-events");
        assertThat(row.getMessageKey()).isEqualTo("100");
        assertThat(row.getEventType()).isEqualTo("LikeAdded");
        assertThat(row.getPayload()).contains("\"likeCount\":5").contains("\"version\":3");
    }

    @DisplayName("ProductViewed → catalog-events outbox row(key=productId, type=ProductViewed).")
    @Test
    void writesCatalogOutboxOnProductViewed() {
        listener.on(new ProductViewed(100L, null, ZonedDateTime.now()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        assertThat(row.getTopic()).isEqualTo("catalog-events");
        assertThat(row.getMessageKey()).isEqualTo("100");
        assertThat(row.getEventType()).isEqualTo("ProductViewed");
        assertThat(row.getPayload()).contains("\"type\":\"ProductViewed\"");
    }

    @DisplayName("OrderPlaced → order-events outbox row(key=orderId, payload에 lines).")
    @Test
    void writesOrderOutboxOnOrderPlaced() {
        listener.on(new OrderPlaced(55L, 7L, 2000L,
            List.of(new OrderPlaced.Line(11L, 2)), ZonedDateTime.now()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        assertThat(row.getTopic()).isEqualTo("order-events");
        assertThat(row.getMessageKey()).isEqualTo("55");
        assertThat(row.getPayload()).contains("\"productId\":11").contains("\"quantity\":2");
    }
}
