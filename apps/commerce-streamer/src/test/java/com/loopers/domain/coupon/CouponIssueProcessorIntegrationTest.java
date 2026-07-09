package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
@Sql(scripts = "/coupon-issue-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CouponIssueProcessorIntegrationTest {

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void seedCoupon(long couponId, int quantity) {
        jdbcTemplate.update("INSERT INTO coupons (id, quantity) VALUES (?, ?)", couponId, quantity);
    }

    private void seedRequest(String requestId, long couponId, long userId) {
        jdbcTemplate.update(
                "INSERT INTO coupon_issue_request (request_id, coupon_id, user_id, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'PENDING', NOW(), NOW())",
                requestId, couponId, userId
        );
    }

    private int couponQuantity(long couponId) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM coupons WHERE id = ?", Integer.class, couponId);
    }

    private String requestStatus(String requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM coupon_issue_request WHERE request_id = ?", String.class, requestId);
    }

    private String requestReason(String requestId) {
        return jdbcTemplate.queryForObject("SELECT reason FROM coupon_issue_request WHERE request_id = ?", String.class, requestId);
    }

    private int userCouponTotal() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_coupons", Integer.class);
    }

    private int countByStatus(String status) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_issue_request WHERE status = ?", Integer.class, status);
    }

    @DisplayName("수량이 남아 있으면, 발급하고 수량을 1 차감하며 요청을 ISSUED 로 만든다.")
    @Test
    void issues_whenQuantityAvailable() {
        seedCoupon(1L, 1);
        seedRequest("req-1", 1L, 10L);

        couponIssueProcessor.process("evt-1", "req-1", 1L, 10L);

        assertThat(couponQuantity(1L)).isEqualTo(0);
        assertThat(requestStatus("req-1")).isEqualTo("ISSUED");
        assertThat(userCouponTotal()).isEqualTo(1);
    }

    @DisplayName("수량이 소진됐으면, 발급하지 않고 요청을 REJECTED(소진) 로 만든다.")
    @Test
    void rejects_whenSoldOut() {
        seedCoupon(1L, 0);
        seedRequest("req-2", 1L, 20L);

        couponIssueProcessor.process("evt-2", "req-2", 1L, 20L);

        assertThat(requestStatus("req-2")).isEqualTo("REJECTED");
        assertThat(requestReason("req-2")).contains("소진");
        assertThat(userCouponTotal()).isEqualTo(0);
    }

    @DisplayName("이미 발급받은 유저면, 수량을 건드리지 않고 요청을 REJECTED(중복) 로 만든다.")
    @Test
    void rejects_whenDuplicateUser() {
        seedCoupon(1L, 5);
        jdbcTemplate.update(
                "INSERT INTO user_coupons (user_id, coupon_id, used, created_at, updated_at) VALUES (10, 1, false, NOW(), NOW())");
        seedRequest("req-3", 1L, 10L);

        couponIssueProcessor.process("evt-3", "req-3", 1L, 10L);

        assertThat(requestStatus("req-3")).isEqualTo("REJECTED");
        assertThat(requestReason("req-3")).contains("이미");
        assertThat(couponQuantity(1L)).isEqualTo(5);
        assertThat(userCouponTotal()).isEqualTo(1);
    }

    @DisplayName("같은 eventId 는 두 번 처리돼도 한 번만 반영된다 (멱등).")
    @Test
    void isIdempotent_onSameEventId() {
        seedCoupon(1L, 1);
        seedRequest("req-4", 1L, 30L);

        couponIssueProcessor.process("evt-4", "req-4", 1L, 30L);
        couponIssueProcessor.process("evt-4", "req-4", 1L, 30L);

        assertThat(couponQuantity(1L)).isEqualTo(0);
        assertThat(userCouponTotal()).isEqualTo(1);
    }

    @DisplayName("동시성: 수량 100 에 요청 200 이 동시에 몰려도 정확히 100 건만 발급되고 초과 발급이 없다.")
    @Test
    void noOverIssue_underConcurrency() throws InterruptedException {
        int quantity = 100;
        int requests = 200;
        seedCoupon(1L, quantity);
        for (int i = 0; i < requests; i++) {
            seedRequest("req-" + i, 1L, i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(20, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch done = new CountDownLatch(requests);
        for (int i = 0; i < requests; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    couponIssueProcessor.process("evt-" + idx, "req-" + idx, 1L, (long) idx);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(couponQuantity(1L)).isEqualTo(0);
        assertThat(countByStatus("ISSUED")).isEqualTo(quantity);
        assertThat(countByStatus("REJECTED")).isEqualTo(requests - quantity);
        assertThat(userCouponTotal()).isEqualTo(quantity);
    }
}