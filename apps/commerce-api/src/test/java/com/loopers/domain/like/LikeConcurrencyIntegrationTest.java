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
 * 좋아요 수 동시성 — 같은 상품에 여러 요청이 동시에 들어와도 likesCount가 정확히 반영되는지 검증한다.
 * 카운터는 원자적 UPDATE(likes_count = likes_count ± 1)로 갱신되므로 read-modify-write의 lost update가 없다.
 *
 * - 서로 다른 N명이 동시에 좋아요 → likesCount == N (lost update 없음)
 * - 같은 사용자가 여러 기기에서 동시에 좋아요 → (user_id, product_id) UNIQUE + 멱등으로 정확히 1
 * - 좋아요 N건 후 N명이 동시에 취소 → likesCount == 0 (음수 없음)
 */
@SpringBootTest
public class LikeConcurrencyIntegrationTest {

    @Autowired LikeService likeService;
    @Autowired ProductService productService;
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

    private long likesCount() {
        return productService.getProduct(productId).getLikesCount();
    }

    @DisplayName("서로 다른 20명이 같은 상품에 동시에 좋아요하면 likesCount는 정확히 20이다(lost update 없음)")
    @Test
    void given_distinctUsers_when_concurrentLike_then_countIsExact() throws InterruptedException {
        runConcurrent(THREADS, userId -> likeService.like(userId, productId));

        assertThat(likesCount()).isEqualTo(THREADS);
    }

    @DisplayName("같은 사용자가 여러 기기에서 동시에 좋아요해도 likesCount는 1이다(멱등)")
    @Test
    void given_sameUser_when_concurrentLikeFromManyDevices_then_countIsOne() throws InterruptedException {
        Long sameUser = 777L;

        runConcurrent(THREADS, ignored -> likeService.like(sameUser, productId));

        assertThat(likesCount()).isEqualTo(1L);
    }

    @DisplayName("20명이 좋아요한 뒤 동시에 취소하면 likesCount는 0이다(음수 없음)")
    @Test
    void given_likedByMany_when_concurrentUnlike_then_countIsZero() throws InterruptedException {
        for (long userId = 0; userId < THREADS; userId++) {
            likeService.like(userId, productId);
        }
        assertThat(likesCount()).isEqualTo(THREADS);

        runConcurrent(THREADS, userId -> likeService.unlike(userId, productId));

        assertThat(likesCount()).isEqualTo(0L);
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
                    // 동시성 충돌(낙관락/UNIQUE 등)은 무시 — 카운터 정합성은 최종 likesCount로 검증
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
