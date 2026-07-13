package com.loopers.queue.application.event;

import com.loopers.order.application.event.OrderCreatedEvent;
import com.loopers.queue.domain.EntryTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueueTokenCleanupListenerTest {

    private final EntryTokenStore entryTokenStore = mock(EntryTokenStore.class);
    private final QueueTokenCleanupListener sut = new QueueTokenCleanupListener(entryTokenStore);

    @Test
    @DisplayName("주문 생성 이벤트를 받으면 해당 유저의 입장 토큰을 삭제한다")
    void givenOrderCreated_whenHandle_thenRemovesToken() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "evt-1", 1L, 42L, "20260706-000001", 29_000L, List.of(), ZonedDateTime.now());

        sut.onOrderCreated(event);

        verify(entryTokenStore).remove("42");
    }
}
