package com.loopers.application.outbox;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.order.OrderPaidEvent;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "outbox.relay.enabled=false")
class OutboxEventHandlerIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 시, catalog-events 로 갈 LIKE_CHANGED 아웃박스가 도메인과 함께 PENDING 으로 적재된다.")
    @Test
    void appendsLikeOutbox() {
        // when
        likeService.like(1L, 100L);

        // then
        List<OutboxModel> rows = outboxJpaRepository.findAll();
        assertThat(rows).hasSize(1);
        OutboxModel row = rows.getFirst();
        assertThat(row.getTopic()).isEqualTo("catalog-events");
        assertThat(row.getEventType()).isEqualTo("LIKE_CHANGED");
        assertThat(row.getAggregateId()).isEqualTo(100L);
        assertThat(row.partitionKey()).isEqualTo("100");
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getPayload()).contains("\"eventType\":\"LIKE_CHANGED\"", "LIKED");
    }

    @DisplayName("상품 상세 조회 기록 시, catalog-events 로 갈 PRODUCT_VIEWED 아웃박스가 적재된다.")
    @Test
    void appendsViewOutbox() {
        // when
        productFacade.recordView(200L, null);

        // then
        List<OutboxModel> rows = outboxJpaRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTopic()).isEqualTo("catalog-events");
        assertThat(rows.get(0).getEventType()).isEqualTo("PRODUCT_VIEWED");
        assertThat(rows.get(0).getAggregateId()).isEqualTo(200L);
    }

    @DisplayName("주문 결제 확정 이벤트 발행 시, order-events 로 갈 ORDER_PAID 아웃박스가 적재된다.")
    @Test
    void appendsOrderPaidOutbox() {
        // given
        OrderPaidEvent event = new OrderPaidEvent(
                "evt-order-1", 999L, 1L, 5000L,
                List.of(new OrderPaidEvent.Item(10L, 2, 10000L)), ZonedDateTime.now()
        );

        // when (커밋되어야 BEFORE_COMMIT 리스너가 발화)
        new TransactionTemplate(txManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));

        // then
        List<OutboxModel> rows = outboxJpaRepository.findAll();
        assertThat(rows).hasSize(1);
        OutboxModel row = rows.getFirst();
        assertThat(row.getTopic()).isEqualTo("order-events");
        assertThat(row.getEventType()).isEqualTo("ORDER_PAID");
        assertThat(row.getAggregateId()).isEqualTo(999L);
        assertThat(row.getEventId()).isEqualTo("evt-order-1");
    }
}