package com.loopers.application.like;

import com.loopers.application.product.LikeCountSyncScheduler;
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
import org.springframework.data.redis.core.RedisTemplate;

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
    private LikeCountSyncScheduler likeCountSyncScheduler;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeJpaRepository productLikeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.delete(redisTemplate.keys("product:like:pending:*"));
    }

    private String likePendingKey(Long productId) {
        return "product:like:pending:" + productId;
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
        String delta = redisTemplate.opsForValue().get(likePendingKey(product.getId()));
        assertAll(
            () -> assertThat(delta).isEqualTo(String.valueOf(threadCount)),
            () -> assertThat(productLikeJpaRepository.count()).isEqualTo(threadCount)
        );
    }

    @DisplayName("여러 유저가 동시에 좋아요와 취소를 반복하면, Redis delta가 실제 좋아요 수와 일치한다.")
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

        // Assert: 30명 모두 홀수 회차 종료 → 전원 좋아요 상태, Redis delta와 실제 레코드 수 일치
        long actualLikeRecords = productLikeJpaRepository.count();
        String delta = redisTemplate.opsForValue().get(likePendingKey(product.getId()));
        assertAll(
            () -> assertThat(delta).isEqualTo(String.valueOf(actualLikeRecords)),
            () -> assertThat(actualLikeRecords).isEqualTo(userCount)
        );
    }

    @DisplayName("좋아요/취소가 여러 sync 사이클에 걸쳐 발생해도, 최종 likeCount가 정확히 반영된다.")
    @Test
    void likeCountIsAccurate_acrossMultipleSyncCycles() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(
            new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));

        // Phase 1: 유저 1~30 동시 좋아요
        int phase1Count = 30;
        ExecutorService executor1 = Executors.newFixedThreadPool(phase1Count);
        CountDownLatch ready1 = new CountDownLatch(phase1Count);
        CountDownLatch start1 = new CountDownLatch(1);
        for (int i = 0; i < phase1Count; i++) {
            final long userId = i + 1L;
            executor1.submit(() -> {
                ready1.countDown();
                try {
                    start1.await();
                    likeFacade.like(new LikeCommand.Like(userId, product.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready1.await();
        start1.countDown();
        executor1.shutdown();
        executor1.awaitTermination(10, TimeUnit.SECONDS);

        likeCountSyncScheduler.productLikeSync();

        // Phase 2: 유저 1~10 취소 + 유저 31~50 좋아요 동시에
        int unlikeCount = 10;
        int newLikeCount = 20;
        CountDownLatch ready = new CountDownLatch(unlikeCount + newLikeCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(unlikeCount + newLikeCount);

        for (int i = 0; i < unlikeCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    likeFacade.unlike(new LikeCommand.Unlike(userId, product.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        for (int i = 0; i < newLikeCount; i++) {
            final long userId = phase1Count + i + 1L;
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

        likeCountSyncScheduler.productLikeSync();

        // Assert: 30 - 10 + 20 = 40
        ProductEntity result = productJpaRepository.findById(product.getId()).orElseThrow();
        long actualLikeRecords = productLikeJpaRepository.count();
        assertAll(
            () -> assertThat(result.getLikeCount()).isEqualTo(40),
            () -> assertThat(actualLikeRecords).isEqualTo(40)
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
        String delta = redisTemplate.opsForValue().get(likePendingKey(product.getId()));
        assertAll(
            () -> assertThat(delta).isEqualTo("1"),
            () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1)
        );
    }
}
