package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueConcurrencyTest {

    @Autowired private CouponIssueProcessor processor;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final int LIMIT = 100;
    private static final int THREADS = 300;
    private Long couponId;

    @BeforeEach
    void setUp() {
        // 선착순 쿠폰 1건 직접 seed (quantity=100, issued_count=0)
        jdbc.update("""
            INSERT INTO coupon (name, type, discount_value, min_order_amount, expired_at, quantity, issued_count, created_at, updated_at)
            VALUES ('선착순', 'FIXED', 1000, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), ?, 0, NOW(), NOW())
            """, LIMIT);
        couponId = jdbc.queryForObject("SELECT id FROM coupon ORDER BY id DESC LIMIT 1", Long.class);
    }

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("서로 다른 300명이 동시에 발급 요청해도 정확히 100장만 발급되고 초과발급은 0이다.")
    @Test
    void noOverIssue_underConcurrency() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            final long userId = 1000L + i; // 서로 다른 유저
            pool.submit(() -> {
                try {
                    start.await();
                    processor.handle(new CouponIssueRequestMessage("req-" + userId, couponId, userId, "t"));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        Long issuedCount = jdbc.queryForObject("SELECT issued_count FROM coupon WHERE id = ?", Long.class, couponId);
        Long userCoupons = jdbc.queryForObject("SELECT COUNT(*) FROM user_coupon WHERE coupon_id = ?", Long.class, couponId);
        Long issuedResults = jdbc.queryForObject(
            "SELECT COUNT(*) FROM coupon_issue_result WHERE coupon_id = ? AND status = ?",
            Long.class, couponId, CouponIssueStatus.ISSUED.name());

        assertThat(issuedCount).isEqualTo((long) LIMIT);   // 초과발급 0
        assertThat(userCoupons).isEqualTo((long) LIMIT);
        assertThat(issuedResults).isEqualTo((long) LIMIT);
        assertThat(errors.get()).isZero();
    }
}
