package com.loopers.tddstudy.application.coupon;

import com.loopers.tddstudy.domain.coupon.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired CouponIssueService couponIssueService;
    @Autowired CouponRepository couponRepository;
    @Autowired UserCouponRepository userCouponRepository;

    @Test
    @DisplayName("선착순 100장에 1000명이 동시 요청해도 정확히 100장만 발급된다")
    void first_come_first_served_limit() throws InterruptedException {
        Coupon coupon = new Coupon("선착순쿠폰", CouponType.FIXED, 1000, 0,
                LocalDateTime.now().plusDays(1), 100);          // maxCount = 100
        Coupon saved = couponRepository.save(coupon);

        int threads = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final long userId = i + 1L;                          // 전원 다른 유저 = 중복 아님
            executor.submit(() -> {
                try {
                    couponIssueService.issue(UUID.randomUUID().toString(), saved.getId(), userId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        Coupon result = couponRepository.findById(saved.getId()).get();
        assertThat(result.getIssuedCount()).isEqualTo(100);                       // 초과 발급 없음
        assertThat(userCouponRepository.findAllByCouponId(saved.getId())).hasSize(100);  // 실제 발급도 100
    }
}
