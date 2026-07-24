package com.loopers.application.outbox;

import com.loopers.application.productlike.ProductLikeFacade;
import com.loopers.confg.kafka.Topics;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 트랜잭션과 같은 트랜잭션에서 outbox 행이 기록되는지 검증한다(BEFORE_COMMIT).
 * Kafka 발행(relay)은 test 프로파일에서 비활성이므로, 여기선 "커밋과 원자적인 outbox 적재"만 확인한다.
 */
@SpringBootTest
class OutboxEventIntegrationTest {

    @Autowired
    private ProductLikeFacade productLikeFacade;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요가 커밋되면 catalog-events용 outbox 행이 PENDING으로 함께 적재된다.")
    @Test
    void writesOutboxRow_onLikeCommit() {
        // arrange
        UserModel user = userJpaRepository.save(new UserModel("user1", "pw1"));
        ProductModel product = productJpaRepository.save(new ProductModel(1L, "상품", "설명", 1000L, 100));

        // act
        productLikeFacade.like("user1", "pw1", product.getId());

        // assert
        List<OutboxEvent> events = outboxEventJpaRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getTopic()).isEqualTo(Topics.CATALOG_EVENTS);
        assertThat(event.getEventType()).isEqualTo("LIKED");
        assertThat(event.getPartitionKey()).isEqualTo(String.valueOf(product.getId()));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPayload()).contains("\"productId\":" + product.getId());
    }

    @DisplayName("멱등: 이미 좋아요한 상태에서 다시 좋아요하면 outbox 행이 추가로 쌓이지 않는다.")
    @Test
    void doesNotWriteOutbox_whenLikeIsIdempotent() {
        // arrange
        userJpaRepository.save(new UserModel("user1", "pw1"));
        ProductModel product = productJpaRepository.save(new ProductModel(1L, "상품", "설명", 1000L, 100));

        // act: 두 번째 좋아요는 insert 0행이라 이벤트가 발행되지 않는다
        productLikeFacade.like("user1", "pw1", product.getId());
        productLikeFacade.like("user1", "pw1", product.getId());

        // assert
        assertThat(outboxEventJpaRepository.findAll()).hasSize(1);
    }
}
