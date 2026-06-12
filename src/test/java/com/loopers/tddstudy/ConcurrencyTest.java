package com.loopers.tddstudy;

import com.loopers.tddstudy.application.like.LikeService;
import com.loopers.tddstudy.application.order.OrderItemRequest;
import com.loopers.tddstudy.application.order.OrderService;
import com.loopers.tddstudy.domain.coupon.Coupon;
import com.loopers.tddstudy.domain.coupon.CouponRepository;
import com.loopers.tddstudy.domain.coupon.CouponType;
import com.loopers.tddstudy.domain.coupon.UserCoupon;
import com.loopers.tddstudy.domain.coupon.UserCouponRepository;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import com.loopers.tddstudy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
public class ConcurrencyTest {
    @Autowired private LikeService likeService;
    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        // 테스트마다 DB 초기화 필요 시 추가
    }

    @Test
    @DisplayName("동일 상품에 동시 주문 시 재고가 정확히 차감된다")
    void concurrentOrder_stockDecrease() throws InterruptedException {
        // given
        Product product = new Product("테스트상품", 10000, 10, 1L);
        product.publish();
        Product saved = productRepository.save(product);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when — 5명이 동시에 2개씩 주문 (재고 10개)
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                try {
                    orderService.createOrder(userId,
                            List.of(new OrderItemRequest(saved.getId(), 2)), null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then — 성공 5번, 재고 0
        Product result = productRepository.findById(saved.getId()).get();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(result.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 쿠폰으로 동시에 주문해도 쿠폰은 단 한번만 사용된다")
    void concurrentOrder_couponUsedOnlyOnce() throws InterruptedException {
        // given
        Product product = new Product("테스트상품", 10000, 100, 1L);
        product.publish();
        Product savedProduct = productRepository.save(product);

        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 0,
                LocalDateTime.now().plusDays(30));
        Coupon savedCoupon = couponRepository.save(coupon);

        // 유저 1이 쿠폰 발급받음
        UserCoupon userCoupon = new UserCoupon(1L, savedCoupon.getId());
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when — 같은 유저가 같은 쿠폰으로 5번 동시 주문
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderService.createOrder(1L,
                            List.of(new OrderItemRequest(savedProduct.getId(), 1)),
                            savedUserCoupon.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then — 딱 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("동일 상품에 동시에 좋아요를 요청해도 좋아요 수가 정확히 반영된다")
    void concurrentLike_likeCountIsAccurate() throws InterruptedException {
        // given
        Product product = new Product("테스트상품", 10000, 100, 1L);
        product.publish();
        Product savedProduct = productRepository.save(product);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when — 10명이 동시에 좋아요
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                try {
                    likeService.addLike(userId, savedProduct.getId());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then — 좋아요 수가 정확히 10
        Product result = productRepository.findById(savedProduct.getId()).get();
        assertThat(result.getLikeCount()).isEqualTo(10);
    }


}
