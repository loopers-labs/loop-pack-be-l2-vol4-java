package com.loopers.application.like;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.ProductLikeJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeFacadeConcurrencyTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeJpaRepository productLikeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("서로 다른 유저가 동시에 좋아요를 누르면, likeCount가 정확히 반영된다.")
    @Test
    void likeCountIsAccurate_whenDifferentUsersLikeConcurrently() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(
            new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    likeFacade.like(new LikeCommand.Like(userId, product.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        ProductEntity result = productJpaRepository.findById(product.getId()).orElseThrow();
        assertAll(
            () -> assertThat(result.getLikeCount()).isEqualTo(threadCount),
            () -> assertThat(productLikeJpaRepository.count()).isEqualTo(threadCount)
        );
    }

    @DisplayName("여러 유저가 동시에 좋아요와 취소를 반복하면, likeCount가 실제 좋아요 수와 일치한다.")
    @Test
    void likeCountMatchesActualLikes_whenUsersToggleLikeConcurrently() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(
            new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));

        // 유저 30명이 동시에 각자 like→unlike→like→unlike→like(5회) 반복
        // 홀수 회차로 종료하므로 모든 유저가 최종적으로 좋아요 상태
        int userCount = 30;
        int toggleCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < userCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int round = 0; round < toggleCount; round++) {
                        if (round % 2 == 0) {
                            likeFacade.like(new LikeCommand.Like(userId, product.getId()));
                        } else {
                            likeFacade.unlike(new LikeCommand.Unlike(userId, product.getId()));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Assert: 30명 모두 홀수 회차 종료 → 전원 좋아요 상태, likeCount와 실제 레코드 수 일치
        long actualLikeRecords = productLikeJpaRepository.count();
        ProductEntity result = productJpaRepository.findById(product.getId()).orElseThrow();
        assertAll(
            () -> assertThat(result.getLikeCount()).isEqualTo(actualLikeRecords),
            () -> assertThat(result.getLikeCount()).isEqualTo(userCount)
        );
    }

    @DisplayName("같은 유저가 동시에 여러 번 좋아요를 누르면, likeCount가 1만 증가한다.")
    @Test
    void likeCountIsOne_whenSameUserLikesConcurrently() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(
            new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    likeFacade.like(new LikeCommand.Like(1L, product.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // PK 충돌(DataIntegrityViolationException)로 인한 예외는 정상 동작
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        ProductEntity result = productJpaRepository.findById(product.getId()).orElseThrow();
        assertAll(
            () -> assertThat(result.getLikeCount()).isEqualTo(1),
            () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1)
        );
    }
}
