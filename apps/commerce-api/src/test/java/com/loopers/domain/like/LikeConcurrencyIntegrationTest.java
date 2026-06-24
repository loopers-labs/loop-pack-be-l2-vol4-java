package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 동시성 — 같은 상품에 여러 요청이 동시에 들어와도 좋아요 "사실"(product_like 활성 행)이 정확한지 검증한다.
 *
 * <p>카운터(likes_count)는 비동기 집계로 분리됐으므로(streamer가 반영), 여기서는 동시성 정확성의 진실원천인
 * <b>활성 좋아요 행 수</b>를 검증한다. 이 정합성이 곧 카운터의 상한이며(이벤트는 실제 전이당 1건), reconcile도
 * 이 행 수로 카운터를 맞춘다. 따라서 행 정합성이 보장되면 최종 카운터도 정확해진다.
 *
 * - 서로 다른 N명이 동시에 좋아요 → 활성 행 N (UNIQUE + 멱등, lost update 없음)
 * - 같은 사용자가 여러 기기에서 동시에 좋아요 → (user_id, product_id) UNIQUE로 정확히 1
 * - 좋아요 N건 후 N명이 동시에 취소 → 활성 행 0
 * - 취소된 행을 동시에 재활성 → 원자적 전이로 활성 행 1 (이중 전이 없음)
 */
@SpringBootTest
public class LikeConcurrencyIntegrationTest {

    @Autowired LikeService likeService;
    @Autowired ProductService productService;
    @Autowired LikeRepository likeRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final int THREADS = 20;
    private Long productId;

    @BeforeEach
    void setUp() {
        ProductModel product = productService.createProduct(1L, "에어맥스", "러닝화", null, 139000L);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 진실원천: 활성 좋아요 행 수. */
    private int activeLikeCount() {
        return likeRepository.findActiveByProductId(productId).size();
    }

    @DisplayName("서로 다른 20명이 같은 상품에 동시에 좋아요하면 활성 좋아요 행은 정확히 20이다(lost update 없음)")
    @Test
    void given_distinctUsers_when_concurrentLike_then_rowsAreExact() throws InterruptedException {
        runConcurrent(THREADS, userId -> likeService.like(userId, productId));

        assertThat(activeLikeCount()).isEqualTo(THREADS);
    }

    @DisplayName("같은 사용자가 여러 기기에서 동시에 좋아요해도 활성 좋아요 행은 1이다(멱등)")
    @Test
    void given_sameUser_when_concurrentLikeFromManyDevices_then_rowIsOne() throws InterruptedException {
        Long sameUser = 777L;

        runConcurrent(THREADS, ignored -> likeService.like(sameUser, productId));

        assertThat(activeLikeCount()).isEqualTo(1);
    }

    @DisplayName("20명이 좋아요한 뒤 동시에 취소하면 활성 좋아요 행은 0이다")
    @Test
    void given_likedByMany_when_concurrentUnlike_then_rowsAreZero() throws InterruptedException {
        for (long userId = 0; userId < THREADS; userId++) {
            likeService.like(userId, productId);
        }
        assertThat(activeLikeCount()).isEqualTo(THREADS);

        runConcurrent(THREADS, userId -> likeService.unlike(userId, productId));

        assertThat(activeLikeCount()).isEqualTo(0);
    }

    @DisplayName("취소한 좋아요를 같은 사용자가 동시에 재등록(reactivate)해도 활성 좋아요 행은 1이다(이중 전이 없음)")
    @Test
    void given_canceledLike_when_concurrentReactivate_then_rowIsOne() throws InterruptedException {
        Long sameUser = 555L;
        likeService.like(sameUser, productId);
        likeService.unlike(sameUser, productId);
        assertThat(activeLikeCount()).isEqualTo(0);   // 취소 상태(비활성 행 존재)

        // 비활성 행을 여러 스레드가 동시에 재활성 — 원자적 전이로 활성 행은 1이어야 한다.
        runConcurrent(THREADS, ignored -> likeService.like(sameUser, productId));

        assertThat(activeLikeCount()).isEqualTo(1);
    }

    /** THREADS개 스레드가 동시에(같은 출발선) userId 0..THREADS-1로 action을 실행한다. */
    private void runConcurrent(int threads, java.util.function.LongConsumer action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (long i = 0; i < threads; i++) {
            final long userId = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.accept(userId);
                } catch (Throwable ignored) {
                    // 동시성 충돌(낙관락/UNIQUE 등)은 무시 — 정합성은 최종 활성 행 수로 검증
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();
    }
}
