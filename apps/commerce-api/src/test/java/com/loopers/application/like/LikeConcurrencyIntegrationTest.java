package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
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
                Product.create(brandId, "상품1", Money.of(1_000L))).getId();
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

        // 좋아요 행(INSERT)은 동기라 즉시 일치한다.
        long rows = likeRepository.countByProductId(productId);
        assertThat(rows).as("실제 좋아요 행 수").isEqualTo(users);

        // 집계(likeCount)는 @Async(AFTER_COMMIT)로 분리되어 eventual 이다 → 수렴까지 대기 후 검사한다.
        long counter = awaitLikeCount(users);
        assertThat(counter).as("비정규화 카운터는 행 수와 정확히 일치해야 한다(수렴 후)").isEqualTo(users);
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
        // 사전 좋아요 집계(@Async)가 모두 수렴(=100)한 뒤 동시 단계를 시작한다 → 이후 발생 이벤트는 취소 50건뿐이라
        // 최종 awaitLikeCount(50) 이 완전 drain 을 보장한다(잔여 async 작업의 다음 테스트 누수 방지).
        assertThat(awaitLikeCount(users)).as("사전 집계 수렴(100)").isEqualTo(users);

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

        // 예외를 삼키지 않고 검증 — 동시 처리 중 어떤 작업도 실패하지 않아야 한다.
        assertThat(exceptions.get()).as("동시 좋아요/취소 중 예외는 발생하지 않아야 한다").isZero();
        // 짝수 50명 취소 반영 → 홀수 50명만 남는다. 멱등 재좋아요는 행 수를 늘리지 않는다.
        assertThat(rows).as("취소 50건이 반영되어 최종 좋아요 행은 50이어야 한다").isEqualTo(50);
        // 집계(likeCount)는 @Async 로 eventual → 수렴까지 대기 후 검사.
        long counter = awaitLikeCount(50);
        assertThat(counter).as("비정규화 카운터도 최종 행 수와 정확히 일치(50)해야 한다(수렴 후)").isEqualTo(50);
    }

    /**
     * 좋아요 집계가 @Async(AFTER_COMMIT)로 분리되어 eventual 이므로, 카운터가 기대값으로 수렴할 때까지
     * 짧게 폴링한다(최대 ~10초). 수렴하지 않으면 마지막 관측값을 그대로 반환해 단언이 실패로 드러나게 한다.
     */
    private long awaitLikeCount(long expected) {
        long counter = -1L;
        for (int i = 0; i < 100; i++) {
            counter = productJpaRepository.findById(productId).orElseThrow().getLikeCount();
            if (counter == expected) {
                return counter;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return counter;
    }
}
