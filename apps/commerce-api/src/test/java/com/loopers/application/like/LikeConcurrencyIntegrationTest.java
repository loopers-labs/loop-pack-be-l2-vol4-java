package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 동시성 통합 테스트.
 *
 * <p>좋아요 수는 카운터 컬럼이 아니라 COUNT 집계로 산출하고 {@code (user_id, product_id)} UK 로
 * 멱등성을 보장하므로, 동시 요청에도 Lost Update 없이 정확히 반영되어야 한다.
 */
@SpringBootTest
class LikeConcurrencyIntegrationTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요해도, 좋아요 수가 정확히 반영된다.")
    @Test
    void countsLikesExactly_whenDifferentUsersLikeConcurrently() throws InterruptedException {
        // arrange — 10명의 서로 다른 유저가 같은 상품에 동시 좋아요
        int threadCount = 10;
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    likeFacade.like(userId, product.getId());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert — 좋아요 수는 정확히 유저 수만큼
        assertThat(likeFacade.countByProductId(product.getId())).isEqualTo(threadCount);
    }

    @DisplayName("같은 유저가 동일 상품에 동시에 여러 번 좋아요해도, 좋아요는 1개만 반영된다 (멱등).")
    @Test
    void countsSingleLike_whenSameUserLikesConcurrently() throws InterruptedException {
        // arrange — 한 유저가 같은 상품에 동시 좋아요 10회
        int threadCount = 10;
        long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    likeFacade.like(userId, product.getId());
                } catch (Exception ignored) {
                    // UK 위반은 멱등 처리되거나 흡수됨
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert — 멱등: 좋아요는 1개만
        assertThat(likeFacade.countByProductId(product.getId())).isEqualTo(1L);
    }
}
