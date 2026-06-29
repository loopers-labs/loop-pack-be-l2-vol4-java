package com.loopers.application.like;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LikeOutboxIntegrationTest {

    private final LikeFacade likeFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final LikeJpaRepository likeJpaRepository;
    private final OutboxJpaRepository outboxJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    // 같은 TX 원자성 검증용: outbox 적재만 골라 실패시킨다. 좋아요 저장 등 나머지는 실제 동작에 위임된다.
    @MockitoSpyBean
    private OutboxEventRepository outboxEventRepository;

    private Long productId;

    @Autowired
    public LikeOutboxIntegrationTest(
        LikeFacade likeFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeJpaRepository likeJpaRepository,
        OutboxJpaRepository outboxJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeJpaRepository = likeJpaRepository;
        this.outboxJpaRepository = outboxJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요가 성공하면, 같은 트랜잭션에서 outbox 에 미발행(published=false) 이벤트가 1건 쌓인다.")
    @Test
    void appendsOutboxRow_whenLikeSucceeds() {
        // given
        Long userId = 1L;

        // when
        likeFacade.like(userId, productId);

        // then
        List<OutboxEvent> rows = outboxJpaRepository.findAll();
        assertAll(
            () -> assertThat(rows).hasSize(1),
            () -> assertThat(rows.get(0).getAggregateId()).isEqualTo(productId),
            () -> assertThat(rows.get(0).getEventType()).isNotBlank(),
            () -> assertThat(rows.get(0).getPayload()).isNotBlank(),
            () -> assertThat(rows.get(0).getEventId()).isNotBlank(),
            () -> assertThat(rows.get(0).isPublished()).isFalse()
        );
    }

    @DisplayName("outbox 적재가 실패하면 좋아요(likes 행)도 함께 롤백된다 — 비즈니스 변경과 outbox 가 한 트랜잭션.")
    @Test
    void rollsBackLike_whenOutboxAppendFails() {
        // given
        Long userId = 1L;
        doThrow(new RuntimeException("outbox 적재 실패"))
            .when(outboxEventRepository).append(any());

        // when
        catchThrowable(() -> likeFacade.like(userId, productId));

        // then
        assertAll(
            () -> assertThat(likeJpaRepository.count()).isZero(),
            () -> assertThat(outboxJpaRepository.count()).isZero()
        );
    }
}
