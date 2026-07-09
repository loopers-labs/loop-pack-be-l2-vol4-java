package com.loopers.interfaces.event.outbox;

import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.interfaces.event.like.LikedEvent;
import com.loopers.interfaces.event.like.UnlikedEvent;
import com.loopers.interfaces.event.order.OrderCreatedEvent;
import com.loopers.interfaces.event.product.ProductViewedEvent;
import java.util.List;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxEventPublisherTest {

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Outbox 이벤트를 저장할 때,")
    @Nested
    class Handle {

        @DisplayName("LikedEvent가 발생하면, catalog-events 토픽으로 outbox 레코드가 저장된다.")
        @Test
        void handle_savesOutbox_whenLiked() {
            // arrange
            LikedEvent event = new LikedEvent(1L, 10L);

            // act
            AopTestUtils.<OutboxEventPublisher>getTargetObject(outboxEventPublisher).handle(event);

            // assert
            var records = outboxJpaRepository.findAll();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getTopic()).isEqualTo("catalog-events");
            assertThat(records.get(0).getPartitionKey()).isEqualTo("10");
        }

        @DisplayName("UnlikedEvent가 발생하면, catalog-events 토픽으로 outbox 레코드가 저장된다.")
        @Test
        void handle_savesOutbox_whenUnliked() {
            // arrange
            UnlikedEvent event = new UnlikedEvent(1L, 10L);

            // act
            AopTestUtils.<OutboxEventPublisher>getTargetObject(outboxEventPublisher).handle(event);

            // assert
            var records = outboxJpaRepository.findAll();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getTopic()).isEqualTo("catalog-events");
            assertThat(records.get(0).getPartitionKey()).isEqualTo("10");
        }

        @DisplayName("ProductViewedEvent가 발생하면, catalog-events 토픽으로 outbox 레코드가 저장된다.")
        @Test
        void handle_savesOutbox_whenProductViewed() {
            // arrange
            ProductViewedEvent event = new ProductViewedEvent(10L, 1L);

            // act
            AopTestUtils.<OutboxEventPublisher>getTargetObject(outboxEventPublisher).handle(event);

            // assert
            var records = outboxJpaRepository.findAll();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getTopic()).isEqualTo("catalog-events");
            assertThat(records.get(0).getPartitionKey()).isEqualTo("10");
        }

        @DisplayName("OrderCreatedEvent가 발생하면, order-events 토픽으로 outbox 레코드가 저장된다.")
        @Test
        void handle_savesOutbox_whenOrderCreated() {
            // arrange
            OrderCreatedEvent event = new OrderCreatedEvent(5L, 1L, 50000L, List.of());

            // act
            AopTestUtils.<OutboxEventPublisher>getTargetObject(outboxEventPublisher).handle(event);

            // assert
            var records = outboxJpaRepository.findAll();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getTopic()).isEqualTo("order-events");
            assertThat(records.get(0).getPartitionKey()).isEqualTo("5");
        }
    }
}
