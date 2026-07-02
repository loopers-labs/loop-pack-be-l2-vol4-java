package com.loopers.interfaces.event.like;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.outbox.OutboxRelay;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeEventType;
import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.UnlikedEvent;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

// fast-path(리스너②)를 격리 검증한다.
// 스케줄 릴레이(③)를 @MockitoBean으로 무력화해야 좋아요 수 변화를 오직 send()에 귀속시킬 수 있다.
// 릴레이가 살아 있으면 send()가 잘못 배선돼도 릴레이가 대신 반영해 테스트가 거짓 통과한다.
@SpringBootTest
class LikeFastPathIntegrationTest {

    @Autowired
    private LikeEventListener listener;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    // 스케줄 릴레이가 대신 반영하지 못하도록 무력화 — 반영 주체를 send()로 한정한다.
    @MockitoBean
    private OutboxRelay outboxRelay;

    // send()는 @Async라 주입된 프록시로 호출하면 별도 스레드로 넘어간다.
    // 프록시를 벗긴 대상 객체로 호출해 본문(인자 배선)을 동기·결정적으로 검증한다.
    private LikeEventListener target;

    @BeforeEach
    void setUp() {
        target = AopTestUtils.getUltimateTargetObject(listener);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel createProductWithStats() {
        BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "상품", BigDecimal.valueOf(1000)));
        productStatsRepository.save(new ProductStatsModel(product));
        return product;
    }

    // record 리스너가 만드는 것과 동일한 PENDING outbox 행을 미리 심는다.
    private void seedPendingOutbox(String eventId, Long productId, LikeEventType eventType, Object event) {
        outboxRepository.save(new OutboxModel(eventId, "Like", String.valueOf(productId), eventType.name(), serialize(event)));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Long likeCountOf(ProductModel product) {
        return productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
    }

    @DisplayName("좋아요 fast-path(send)를 처리할 때,")
    @Nested
    class LikedFastPath {

        @DisplayName("PENDING 아웃박스가 있으면 like_count가 1 증가하고 아웃박스가 DONE 처리된다.")
        @Test
        void increasesLikeCountAndMarksDone_whenLikedEventSent() {
            // given
            ProductModel product = createProductWithStats();
            String eventId = UUID.randomUUID().toString();
            LikedEvent event = new LikedEvent(eventId, 1L, product.getId());
            seedPendingOutbox(eventId, product.getId(), LikeEventType.LIKED_EVENT, event);

            // when
            target.send(event);

            // then
            List<OutboxModel> pending = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                () -> assertThat(likeCountOf(product)).isEqualTo(1L),
                () -> assertThat(pending).isEmpty()
            );
        }

        @DisplayName("동일 이벤트로 두 번 처리해도 like_count는 한 번만 증가한다.")
        @Test
        void isIdempotent_whenSameEventSentTwice() {
            // given
            ProductModel product = createProductWithStats();
            String eventId = UUID.randomUUID().toString();
            LikedEvent event = new LikedEvent(eventId, 1L, product.getId());
            seedPendingOutbox(eventId, product.getId(), LikeEventType.LIKED_EVENT, event);

            // when
            target.send(event);
            target.send(event);

            // then
            assertThat(likeCountOf(product)).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요 취소 fast-path(send)를 처리할 때,")
    @Nested
    class UnlikedFastPath {

        @DisplayName("PENDING 아웃박스가 있으면 like_count가 1 감소한다.")
        @Test
        void decreasesLikeCount_whenUnlikedEventSent() {
            // given: 먼저 좋아요 1개 반영된 상태
            ProductModel product = createProductWithStats();
            String likedEventId = UUID.randomUUID().toString();
            LikedEvent liked = new LikedEvent(likedEventId, 1L, product.getId());
            seedPendingOutbox(likedEventId, product.getId(), LikeEventType.LIKED_EVENT, liked);
            target.send(liked);

            String unlikedEventId = UUID.randomUUID().toString();
            UnlikedEvent unliked = new UnlikedEvent(unlikedEventId, 1L, product.getId());
            seedPendingOutbox(unlikedEventId, product.getId(), LikeEventType.UNLIKED_EVENT, unliked);

            // when
            target.send(unliked);

            // then
            assertThat(likeCountOf(product)).isEqualTo(0L);
        }
    }
}
