package com.loopers.application.like;

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
import com.loopers.domain.outbox.OutboxService;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeOutboxRelayIntegrationTest {

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxService outboxService;

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

    private void savePendingOutbox(Long productId, LikeEventType eventType) {
        String eventId = UUID.randomUUID().toString();
        Object event = eventType == LikeEventType.LIKED_EVENT
                ? new LikedEvent(eventId, 1L, productId)
                : new UnlikedEvent(eventId, 1L, productId);
        outboxRepository.save(new OutboxModel(eventId, "Like", String.valueOf(productId), eventType.name(), serialize(event)));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // 이 테스트 파일은 like_count 반영(T1)과 T2 아웃박스 기록 여부만 검증한다 - T2의 실제 Kafka 발행은
    // 실제 브로커를 띄우는 ProductLikeKafkaOutboxEventHandlerIntegrationTest에서 별도로 검증한다.
    // relay()를 다시 호출하기 전에 leftover T2(PRODUCT_LIKED/PRODUCT_UNLIKED) PENDING 행을 미리 DONE 처리해
    // 다음 relay()가 실제 브로커로 발행을 시도하지 않도록 한다.
    private void settlePendingProductKafkaEvents() {
        outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING).stream()
                .filter(o -> o.getEventType().equals("PRODUCT_LIKED") || o.getEventType().equals("PRODUCT_UNLIKED"))
                .forEach(o -> outboxService.markDoneIfPending(o.getEventId()));
    }

    @DisplayName("relay()를 호출할 때,")
    @Nested
    class Relay {

        @DisplayName("LIKED_EVENT 아웃박스가 있으면 like_count가 1 증가하고 LIKED_EVENT PENDING 아웃박스가 없어진다.")
        @Test
        void increasesLikeCount_whenLikedEventIsPending() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);

            // when
            outboxRelay.relay();

            // then: T1(LIKED_EVENT)은 처리 완료된다 - T2(PRODUCT_LIKED)의 새 PENDING 행은 별도로 검증한다.
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            List<OutboxModel> remainingLikedEvents = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING).stream()
                    .filter(o -> o.getEventType().equals(LikeEventType.LIKED_EVENT.name()))
                    .toList();
            assertAll(
                () -> assertThat(likeCount).isEqualTo(1L),
                () -> assertThat(remainingLikedEvents).isEmpty()
            );
        }

        @DisplayName("UNLIKED_EVENT 아웃박스가 있으면 like_count가 1 감소하고 UNLIKED_EVENT PENDING 아웃박스가 없어진다.")
        @Test
        void decreasesLikeCount_whenUnlikedEventIsPending() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);
            outboxRelay.relay();
            settlePendingProductKafkaEvents();

            savePendingOutbox(product.getId(), LikeEventType.UNLIKED_EVENT);

            // when
            outboxRelay.relay();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            List<OutboxModel> remainingUnlikedEvents = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING).stream()
                    .filter(o -> o.getEventType().equals(LikeEventType.UNLIKED_EVENT.name()))
                    .toList();
            assertAll(
                () -> assertThat(likeCount).isEqualTo(0L),
                () -> assertThat(remainingUnlikedEvents).isEmpty()
            );
        }

        @DisplayName("relay()를 두 번 호출해도 like_count는 한 번만 변경된다.")
        @Test
        void isIdempotent_whenRelayCalledTwice() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);

            // when
            outboxRelay.relay();
            settlePendingProductKafkaEvents();
            outboxRelay.relay();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(1L);
        }

        @DisplayName("LIKED_EVENT 아웃박스를 반영하면 T2 시점의 PRODUCT_LIKED 아웃박스가 새로 기록된다.")
        @Test
        void recordsProductLikedOutbox_whenLikedEventIsReflected() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);

            // when
            outboxRelay.relay();

            // then
            List<OutboxModel> pending = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                () -> assertThat(pending).hasSize(1),
                () -> assertThat(pending.get(0).getEventType()).isEqualTo("PRODUCT_LIKED"),
                () -> assertThat(pending.get(0).getAggregateType()).isEqualTo("Product"),
                () -> assertThat(pending.get(0).getAggregateId()).isEqualTo(String.valueOf(product.getId()))
            );
        }

        @DisplayName("UNLIKED_EVENT 아웃박스를 반영하면 T2 시점의 PRODUCT_UNLIKED 아웃박스가 새로 기록된다.")
        @Test
        void recordsProductUnlikedOutbox_whenUnlikedEventIsReflected() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);
            outboxRelay.relay();
            settlePendingProductKafkaEvents();

            savePendingOutbox(product.getId(), LikeEventType.UNLIKED_EVENT);

            // when
            outboxRelay.relay();

            // then
            List<OutboxModel> pending = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                () -> assertThat(pending).hasSize(1),
                () -> assertThat(pending.get(0).getEventType()).isEqualTo("PRODUCT_UNLIKED"),
                () -> assertThat(pending.get(0).getAggregateId()).isEqualTo(String.valueOf(product.getId()))
            );
        }
    }
}
