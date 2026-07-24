package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStockModel;
import com.loopers.infrastructure.coupon.CouponStockJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 발급의 핵심 보장을 실제 MySQL에서 직접 검증한다(파티션 직렬화 없이 순수 동시 호출).
 * <p>
 * 조건부 UPDATE(WHERE issued &lt; quota)와 (user_id, coupon_id) UNIQUE 제약만으로
 * ①수량 초과 발급이 없고 ②한 유저가 여러 번 요청해도 1장만 나가는지를 증명한다.
 */
@SpringBootTest
class CouponIssueConcurrencyIntegrationTest {

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private CouponStockJpaRepository couponStockJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("100장 한정 쿠폰에 300명이 동시에 요청해도 정확히 100장만 발급된다(초과 없음).")
    @Test
    void doesNotOverIssue_underConcurrency() throws InterruptedException {
        Long couponId = 9001L;
        int quota = 100;
        int attempts = 300;
        Long stockId = couponStockJpaRepository.save(new CouponStockModel(couponId, quota)).getId();

        runConcurrently(attempts, i -> couponIssueService.issue(UUID.randomUUID().toString(), couponId, (long) i));

        // 정확히 quota 만큼만 발급되고, 재고 카운터도 quota에서 멈춘다(101로 넘지 않음).
        assertThat(userCouponJpaRepository.count()).isEqualTo(quota);
        assertThat(couponStockJpaRepository.findById(stockId).orElseThrow().getIssued()).isEqualTo(quota);
    }

    @DisplayName("멱등: 같은 유저가 동시에 여러 번 요청해도 1장만 발급된다.")
    @Test
    void issuesOnlyOncePerUser_underConcurrency() throws InterruptedException {
        Long couponId = 9002L;
        Long sameUserId = 1L;
        int attempts = 30;
        Long stockId = couponStockJpaRepository.save(new CouponStockModel(couponId, 100)).getId();

        runConcurrently(attempts, i -> couponIssueService.issue(UUID.randomUUID().toString(), couponId, sameUserId));

        // exists 체크 + UNIQUE 제약으로 한 유저에게 1장만.
        assertThat(userCouponJpaRepository.count()).isEqualTo(1L);
        assertThat(couponStockJpaRepository.findById(stockId).orElseThrow().getIssued()).isEqualTo(1);
    }

    /** attempts개의 작업을 동시에 시작(start latch)해 최대 경합을 유도한 뒤, 전부 끝날 때까지 기다린다. */
    private void runConcurrently(int attempts, java.util.function.IntConsumer task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(24);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        for (int i = 1; i <= attempts; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    task.accept(idx);
                } catch (Exception ignored) {
                    // 소진(스킵)·중복(UNIQUE 위반 롤백) 등으로 실패할 수 있다 — 최종 DB 상태로 검증한다.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
    }
}
