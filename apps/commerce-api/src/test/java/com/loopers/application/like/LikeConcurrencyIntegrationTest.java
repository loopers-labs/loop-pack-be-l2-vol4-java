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
import java.util.concurrent.atomic.AtomicInteger;

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

    @DisplayName("동일 상품에 좋아요와 취소가 뒤섞여 동시에 들어와도, 취소 50건이 반영되어 행·카운터가 정확히 50이 된다.")
    @Test
    void concurrentLikeAndCancel_countMatchesRows() throws InterruptedException {
        int users = 100;
        // 100명 전원을 먼저 좋아요 상태로 만들어 둔다.
        for (int i = 0; i < users; i++) {
            likeApplicationService.register(1_000L + i, productId);
        }
        assertThat(likeRepository.countByProductId(productId)).as("사전 상태: 100건 좋아요").isEqualTo(users);

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(users);
        AtomicInteger exceptions = new AtomicInteger();

        for (int i = 0; i < users; i++) {
            final long userId = 1_000L + i;
            final boolean cancel = i % 2 == 0; // 짝수 50명은 취소, 홀수 50명은 재좋아요(멱등)
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (cancel) {
                        likeApplicationService.cancel(userId, productId);
                    } else {
                        likeApplicationService.register(userId, productId);
                    }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
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

        // 예외를 삼키지 않고 검증 — 동시 처리 중 어떤 작업도 실패하지 않아야 한다.
        assertThat(exceptions.get()).as("동시 좋아요/취소 중 예외는 발생하지 않아야 한다").isZero();
        // 짝수 50명 취소 반영 → 홀수 50명만 남는다. 멱등 재좋아요는 행 수를 늘리지 않는다.
        assertThat(rows).as("취소 50건이 반영되어 최종 좋아요 행은 50이어야 한다").isEqualTo(50);
        assertThat(counter).as("비정규화 카운터도 최종 행 수와 정확히 일치(50)해야 한다").isEqualTo(50);
    }
}
