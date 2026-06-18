package com.loopers.domain.coupon;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 여러 주문이 동시에 요청되어도, 쿠폰은 단 한 번만 사용된다.")
    @Test
    void couponUsedOnlyOnce_underConcurrentOrders() throws InterruptedException {
        // given
        Long userId = 1L;
        int threadCount = 10;
        ProductModel product = productService.createProduct(1L, "티셔츠", "면 100%", 10000L, threadCount);
        CouponModel coupon = couponService.createCoupon("정액 1000원", CouponType.FIXED, 1000L, null,
                LocalDateTime.of(2999, 12, 31, 23, 59, 59), null);
        userCouponService.issue(userId, coupon.getId());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.createPendingOrder(userId, List.of(OrderLine.of(product.getId(), 1)), coupon.getId());
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // then
        assertAll(
                () -> assertThat(success.get()).isEqualTo(1),
                () -> assertThat(failure.get()).isEqualTo(threadCount - 1),
                () -> assertThat(userCouponService.getMyCoupons(userId).get(0).isUsed()).isTrue()
        );
    }

    @DisplayName("한정 수량 쿠폰을 여러 유저가 동시에 발급해도, 수량만큼만 발급된다.")
    @Test
    void couponIssuedOnlyUpToQuantity_underConcurrentIssue() throws InterruptedException {
        // given
        int quantity = 3;
        int threadCount = 10;
        CouponModel coupon = couponService.createCoupon("한정 쿠폰", CouponType.FIXED, 1000L, null,
                LocalDateTime.of(2999, 12, 31, 23, 59, 59), quantity);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // when (유저별로 서로 다른 id 로 동시에 발급 요청)
        for (int i = 0; i < threadCount; i++) {
            long uid = i + 1;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    userCouponService.issue(uid, coupon.getId());
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // then
        assertAll(
                () -> assertThat(success.get()).isEqualTo(quantity),
                () -> assertThat(failure.get()).isEqualTo(threadCount - quantity),
                () -> assertThat(couponService.getCoupon(coupon.getId()).getQuantity()).isEqualTo(0)
        );
    }
}