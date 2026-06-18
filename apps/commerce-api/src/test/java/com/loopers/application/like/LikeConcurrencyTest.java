package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("concurrency")
@SpringBootTest
class LikeConcurrencyTest {

    @Autowired private LikeApplicationService likeApplicationService;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private LikeJpaRepository likeJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 동시성 테스트")
    @Nested
    class LikeCount {

        @DisplayName("동일한 상품에 N명이 동시에 좋아요를 요청하면 좋아요 수가 정확히 N개 반영된다.")
        @Test
        void likeCountIsAccurate_underConcurrentRequests() throws InterruptedException {
            int THREADS = 10;

            BrandEntity brand = brandJpaRepository.save(BrandEntity.from(new BrandModel("브랜드A", "설명")));
            ProductEntity product = productJpaRepository.save(ProductEntity.from(
                new ProductModel(null, brand.getId(), "상품A", "설명", 10_000L, 100, 0L, null, null), brand
            ));
            Long productId = product.getId();

            AtomicInteger successCount = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);

            for (int i = 0; i < THREADS; i++) {
                final long userId = i + 1L;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        likeApplicationService.like(userId, productId);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            executor.shutdown();

            long likeCount = likeJpaRepository.countByProductId(productId);
            assertThat(successCount.get()).isEqualTo(THREADS);
            assertThat(likeCount).isEqualTo(THREADS);
        }
    }
}
