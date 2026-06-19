package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeEventType;
import com.loopers.domain.like.LikeOutboxModel;
import com.loopers.domain.like.LikeOutboxRepository;
import com.loopers.domain.like.OutboxStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeOutboxProcessorIntegrationTest {

    @Autowired
    private LikeOutboxProcessor likeOutboxProcessor;

    @Autowired
    private LikeOutboxRepository likeOutboxRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

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

    @DisplayName("process()를 호출할 때,")
    @Nested
    class Process {

        @DisplayName("LIKED_EVENT 아웃박스가 있으면 like_count가 1 증가하고 PENDING 아웃박스가 없어진다.")
        @Test
        void increasesLikeCount_whenLikedEventIsPending() {
            // given
            ProductModel product = createProductWithStats();
            likeOutboxRepository.save(new LikeOutboxModel(product.getId(), LikeEventType.LIKED_EVENT));

            // when
            likeOutboxProcessor.process();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            List<LikeOutboxModel> remaining = likeOutboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
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
            likeOutboxRepository.save(new LikeOutboxModel(product.getId(), LikeEventType.LIKED_EVENT));
            likeOutboxProcessor.process();

            likeOutboxRepository.save(new LikeOutboxModel(product.getId(), LikeEventType.UNLIKED_EVENT));

            // when
            likeOutboxProcessor.process();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            List<LikeOutboxModel> remaining = likeOutboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                () -> assertThat(likeCount).isEqualTo(0L),
                () -> assertThat(remaining).isEmpty()
            );
        }

        @DisplayName("process()를 두 번 호출해도 like_count는 한 번만 변경된다.")
        @Test
        void isIdempotent_whenProcessCalledTwice() {
            // given
            ProductModel product = createProductWithStats();
            likeOutboxRepository.save(new LikeOutboxModel(product.getId(), LikeEventType.LIKED_EVENT));

            // when
            likeOutboxProcessor.process();
            likeOutboxProcessor.process();

            // then
            Long likeCount = productStatsRepository.findByProduct(product).orElseThrow().getLikeCount();
            assertThat(likeCount).isEqualTo(1L);
        }
    }
}
