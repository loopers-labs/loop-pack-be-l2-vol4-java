package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("좋아요 수 동시성")
@SpringBootTest
class LikeConcurrencyIntegrationTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(
                Product.create(brandId, "상품1", Money.of(1_000L), Stock.of(10))).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("서로 다른 M명이 같은 상품에 동시에 좋아요를 누르면, 좋아요 수(카운터)는 정확히 M 이고 행 수와 일치한다.")
    @Test
    void concurrentLikes_countIsExact() throws InterruptedException {
        int users = 100;

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(users);

        for (int i = 0; i < users; i++) {
            final long userId = 1_000L + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    likeApplicationService.register(userId, productId);
                } catch (Exception ignored) {
                    // 동시성 결함이 있으면 카운터 불일치로 드러나므로 여기서는 삼킨다
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 좋아요 시도가 30초 내에 끝나야 한다").isTrue();

        long rows = likeRepository.countByProductId(productId);
        long counter = productJpaRepository.findById(productId).orElseThrow().getLikeCount();

        assertThat(rows).as("실제 좋아요 행 수").isEqualTo(users);
        assertThat(counter).as("비정규화 카운터는 행 수와 정확히 일치해야 한다").isEqualTo(users);
    }

    @DisplayName("동일 상품에 좋아요와 취소가 뒤섞여 동시에 들어와도, 카운터는 최종 행 수와 일치한다.")
    @Test
    void concurrentLikeAndCancel_countMatchesRows() throws InterruptedException {
        int users = 100;
        // 먼저 절반은 좋아요 상태로 만들어 둔다 — 이들에 대한 취소가 섞이게.
        for (int i = 0; i < users; i++) {
            likeApplicationService.register(1_000L + i, productId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(users);

        for (int i = 0; i < users; i++) {
            final long userId = 1_000L + i;
            final boolean cancel = i % 2 == 0; // 짝수 유저는 취소, 홀수 유저는 재좋아요(멱등)
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (cancel) {
                        likeApplicationService.cancel(userId, productId);
                    } else {
                        likeApplicationService.register(userId, productId);
                    }
                } catch (Exception ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 시도가 30초 내에 끝나야 한다").isTrue();

        long rows = likeRepository.countByProductId(productId);
        long counter = productJpaRepository.findById(productId).orElseThrow().getLikeCount();

        assertThat(counter).as("카운터는 최종 좋아요 행 수와 일치해야 한다").isEqualTo(rows);
    }
}
