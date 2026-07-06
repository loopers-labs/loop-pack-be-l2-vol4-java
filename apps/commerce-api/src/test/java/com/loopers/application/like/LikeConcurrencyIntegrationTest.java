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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 동시성 통합 테스트.
 *
 * <p>좋아요는 동기로 즉시 성공하고, like_count 집계는 커밋 후 {@code @Async} 리스너가 <strong>최종 일관성</strong>으로
 * 반영한다(Round 7). UK 로 멱등성을, 원자 UPDATE 로 동시성을 보장하므로, 집계가 반영된 뒤에는 Lost Update 없이
 * 정확히 유저 수만큼 카운트되어야 한다. 집계가 비동기라 like() 반환 직후가 아니라 잠시 뒤 반영되므로 폴링으로 대기한다.
 */
@SpringBootTest
class LikeConcurrencyIntegrationTest {

    @Autowired private LikeApplicationService likeApplicationService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 여러 명이 동시에 좋아요해도, 집계 반영 후 좋아요 수가 정확히 반영된다.")
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
                    likeApplicationService.like(userId, product.getId());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 비동기 집계가 반영되면 like_count 가 정확히 유저 수만큼 (원자 UPDATE 로 Lost Update 없음)
        assertThat(awaitLikeCount(product.getId(), threadCount)).isEqualTo(threadCount);
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
                    likeApplicationService.like(userId, product.getId());
                } catch (Exception ignored) {
                    // UK 위반은 멱등 처리되거나 흡수됨
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 멱등: like_count 는 1 (중복 등록이 count 를 올리지 않음)
        assertThat(awaitLikeCount(product.getId(), 1L)).isEqualTo(1L);
    }

    /**
     * 비동기 집계가 반영될 때까지 like_count 를 폴링한다. expected 도달 시 즉시 반환하고,
     * 타임아웃되면 마지막으로 읽은 값을 반환한다(불일치 시 호출부 단언에서 실패로 드러남).
     */
    private long awaitLikeCount(Long productId, long expected) {
        long deadline = System.currentTimeMillis() + 10_000L;
        long actual = -1L;
        while (System.currentTimeMillis() < deadline) {
            actual = productRepository.findById(productId).orElseThrow().getLikeCount();
            if (actual == expected) {
                return actual;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return actual;
    }
}
