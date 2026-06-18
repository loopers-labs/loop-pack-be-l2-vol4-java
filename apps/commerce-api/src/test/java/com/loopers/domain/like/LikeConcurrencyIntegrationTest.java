package com.loopers.domain.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.RedisLikeCountStore;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 수 동시성 검증. 여러 유저가 같은 상품에 동시에 좋아요해도 증감분이 정확히 반영되어야 한다.
 * 좋아요 수는 Redis INCRBY로 흡수되며, INCRBY는 원자적이라 동시 요청에서도 증감분이 유실되지 않는다.
 */
@SpringBootTest
class LikeConcurrencyIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String delta(Long productId) {
        return masterRedisTemplate.opsForValue().get(RedisLikeCountStore.DELTA_KEY_PREFIX + productId);
    }

    @DisplayName("여러 명이 동시에 같은 상품을 좋아요하면 좋아요 증감분이 정확히 사람 수만큼 누적된다")
    @Test
    void deltaReflectsAllConcurrentLikes() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        Long productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when - 서로 다른 유저 10명이 동시에 좋아요
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeFacade.like(userId, productId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then - 증감분이 정확히 10
        assertThat(delta(productId)).isEqualTo("10");
    }

    @DisplayName("여러 명이 동시에 같은 상품을 좋아요/취소하면 증감분이 정확히 0으로 상쇄된다")
    @Test
    void deltaNetsToZero_whenAllUnlikeAfterLike() throws InterruptedException {
        // given - 상품 + 10명이 미리 좋아요 (증감분 10)
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        Long productId = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();

        int threadCount = 10;
        for (int i = 0; i < threadCount; i++) {
            likeFacade.like((long) (i + 1), productId);
        }
        assertThat(delta(productId)).isEqualTo("10");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when - 좋아요한 10명이 동시에 취소
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeFacade.unlike(userId, productId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then - 증감분이 정확히 0 (유실 없음)
        assertThat(delta(productId)).isEqualTo("0");
    }
}
