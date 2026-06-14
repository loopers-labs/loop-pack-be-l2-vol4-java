package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
    }

    @DisplayName("동시에 좋아요를 요청할 때, ")
    @Nested
    class ConcurrentLike {
        @DisplayName("서로 다른 유저 10명이 동시에 좋아요를 눌러도, 좋아요 수가 10으로 정상 반영된다.")
        @Test
        void reflectsAllLikes_whenTenUsersLikeConcurrently() throws Exception {
            // arrange
            Product product = saveProduct();
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // act
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        likeFacade.like(userId, product.getId());
                    } catch (Throwable ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(reloaded.getLikeCount()).isEqualTo(10L);
        }

        @DisplayName("좋아요한 유저 10명이 동시에 취소해도, 좋아요 수가 0으로 정상 반영된다.")
        @Test
        void reflectsAllUnlikes_whenTenUsersUnlikeConcurrently() throws Exception {
            // arrange
            Product product = saveProduct();
            int threadCount = 10;
            for (int i = 0; i < threadCount; i++) {
                likeFacade.like(i + 1L, product.getId());
            }
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // act
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        likeFacade.unlike(userId, product.getId());
                    } catch (Throwable ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(reloaded.getLikeCount()).isEqualTo(0L);
        }
    }
}
