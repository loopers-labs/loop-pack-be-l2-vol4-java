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

    @DisplayName("relay()를 호출할 때,")
    @Nested
    class Relay {

        @DisplayName("LIKED_EVENT 아웃박스가 있으면 like_count가 1 증가하고 PENDING 아웃박스가 없어진다.")
        @Test
        void increasesLikeCount_whenLikedEventIsPending() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);

            // when
            outboxRelay.relay();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            List<OutboxModel> remaining = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                () -> assertThat(likeCount).isEqualTo(1L),
                () -> assertThat(remaining).isEmpty()
            );
        }

        @DisplayName("UNLIKED_EVENT 아웃박스가 있으면 like_count가 1 감소하고 PENDING 아웃박스가 없어진다.")
        @Test
        void decreasesLikeCount_whenUnlikedEventIsPending() {
            // given
            ProductModel product = createProductWithStats();
            savePendingOutbox(product.getId(), LikeEventType.LIKED_EVENT);
            outboxRelay.relay();

            savePendingOutbox(product.getId(), LikeEventType.UNLIKED_EVENT);

            // when
            outboxRelay.relay();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            List<OutboxModel> remaining = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                () -> assertThat(likeCount).isEqualTo(0L),
                () -> assertThat(remaining).isEmpty()
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
            outboxRelay.relay();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(1L);
        }
    }
}
