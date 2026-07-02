package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.money.Money;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OutboxIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;
    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
    }

    @DisplayName("좋아요를 누르면, catalog-events 로 향하는 PENDING outbox 행이 함께 기록된다.")
    @Test
    void recordsOutboxEvent_whenProductIsLiked() {
        Product product = saveProduct();

        likeFacade.like(1L, product.getId());

        List<OutboxEvent> events = outboxEventRepository.findPending(10);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getTopic()).isEqualTo(OutboxRecorder.CATALOG_TOPIC);
            assertThat(event.getEventType()).isEqualTo("ProductLikedEvent");
            assertThat(event.getPartitionKey()).isEqualTo(String.valueOf(product.getId()));
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getPayload()).contains(event.getEventId());
        });
    }

    @DisplayName("주문을 생성하면, order-events 로 향하는 PENDING outbox 행이 함께 기록된다.")
    @Test
    void recordsOutboxEvent_whenOrderIsPlaced() {
        Product product = saveProduct();

        OrderInfo info = orderFacade.place(1L, List.of(new OrderLineCommand(product.getId(), 2)), null);

        List<OutboxEvent> events = outboxEventRepository.findPending(10);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getTopic()).isEqualTo(OutboxRecorder.ORDER_TOPIC);
            assertThat(event.getEventType()).isEqualTo("OrderCreatedEvent");
            assertThat(event.getPartitionKey()).isEqualTo(String.valueOf(info.id()));
        });
    }

    @DisplayName("이벤트를 발행한 트랜잭션이 롤백되면, outbox 행도 함께 사라진다.")
    @Test
    void discardsOutboxEvent_whenPublishingTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new ProductLikedEvent(1L, 999L));
            status.setRollbackOnly();
        });

        assertThat(outboxEventRepository.findPending(10)).isEmpty();
    }

    @DisplayName("릴레이가 PENDING 이벤트를 Kafka 로 발행하고 PUBLISHED 로 표시한다.")
    @Test
    void publishesPendingEventsAndMarksPublished_whenRelayRuns() {
        Product product = saveProduct();
        likeFacade.like(1L, product.getId());
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        given(kafkaTemplate.send(anyString(), anyString(), any()))
            .willReturn(CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(outboxEventRepository, kafkaTemplate, objectMapper);

        relay.publishPending();

        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.eq(OutboxRecorder.CATALOG_TOPIC),
            org.mockito.ArgumentMatchers.eq(String.valueOf(product.getId())),
            any());
        assertThat(outboxEventRepository.findPending(10)).isEmpty();
        assertThat(outboxEventJpaRepository.findAll())
            .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED));
    }
}
