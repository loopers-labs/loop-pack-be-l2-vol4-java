package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.outbox.OutboxEntity;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 변경이 <b>같은 트랜잭션</b>으로 outbox에 적재되는지 검증한다(BEFORE_COMMIT 배선, Slice 2).
 *
 * <p>{@code LikeService.like/unlike}가 발행하는 {@code LikeChangedEvent}를 {@link LikeEventOutboxListener}가
 * 커밋 직전에 받아 outbox 행으로 남긴다. 검증 포인트: (1) 전이가 일어난 만큼만 적재(멱등 no-op은 미적재),
 * (2) 토픽/키/payload 계약, (3) status=PENDING(릴레이는 test 프로파일에서 비활성이라 그대로 남는다).
 */
@SpringBootTest
public class LikeEventOutboxIntegrationTest {

    @Autowired LikeService likeService;
    @Autowired ProductService productService;
    @Autowired OutboxJpaRepository outboxJpaRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        ProductModel product = productService.createProduct(1L, "에어맥스", "러닝화", null, 139000L);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private List<OutboxEntity> likeChangedRows() {
        return outboxJpaRepository.findAll().stream()
                .filter(o -> "LIKE_CHANGED".equals(o.getEventType()))
                .toList();
    }

    @DisplayName("좋아요하면 LIKE_CHANGED(delta +1) 이벤트가 catalog-events outbox에 PENDING으로 적재된다.")
    @Test
    void given_like_then_outboxAppended() {
        likeService.like(USER_ID, productId);

        assertThat(likeChangedRows()).singleElement().satisfies(o -> {
            assertThat(o.getTopic()).isEqualTo("catalog-events");
            assertThat(o.getAggregateType()).isEqualTo("product");
            assertThat(o.getAggregateId()).isEqualTo(productId);
            assertThat(o.getPartitionKey()).isEqualTo(String.valueOf(productId));
            assertThat(o.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(o.getPayload()).contains("\"productId\":" + productId).contains("\"delta\":1");
        });
    }

    @DisplayName("멱등 no-op(이미 좋아요 상태에서 재좋아요)은 outbox에 적재되지 않는다 — 전이 1건만 기록.")
    @Test
    void given_duplicateLike_then_onlyOneOutboxRow() {
        likeService.like(USER_ID, productId);
        likeService.like(USER_ID, productId);

        assertThat(likeChangedRows()).hasSize(1);
    }

    @DisplayName("좋아요 후 취소하면 +1, -1 두 건이 순서대로 적재된다.")
    @Test
    void given_likeThenUnlike_then_twoOutboxRows() {
        likeService.like(USER_ID, productId);
        likeService.unlike(USER_ID, productId);

        List<OutboxEntity> rows = likeChangedRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getPayload()).contains("\"delta\":1");
        assertThat(rows.get(1).getPayload()).contains("\"delta\":-1");
    }
}
