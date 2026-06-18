package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.coupon.*;
import com.loopers.application.order.OrderRepository;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.application.payment.PaymentRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderFacadeConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("?ш퀬媛 5媛??덈뒗 ?곹뭹?????10紐낆쓽 ?ъ슜?먭? ?숈떆??checkout???붿껌?섎㈃ 5紐낅쭔 ?깃났?섍퀬 5紐낆? ?ㅽ뙣?쒕떎.")
    void checkout_ConcurrentStockDecrease_ShouldLimitToStockQuantity() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(5);
        product = productRepository.save(product);
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        barrier.await();
                        OrderCheckoutRequest request = new OrderCheckoutRequest(
                                List.of(new OrderCheckoutRequest.Item(productId, 1)),
                                null,
                                PaymentMethod.CARD
                        );
                        orderFacade.checkout(userId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);

            // ?⑥? ?ш퀬 ?뺤씤
            ProductModel updatedProduct = productRepository.findById(productId).orElseThrow();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(0);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("?숈씪??荑좏룿???ъ슜?섏뿬 10媛쒖쓽 ?ㅻ젅?쒖뿉???숈떆??checkout???붿껌?섎㈃ 1媛쒕쭔 ?깃났?섍퀬 ?섎㉧吏???ㅽ뙣?쒕떎. (Double Spending 諛⑹뼱)")
    void checkout_ConcurrentCouponUse_ShouldPreventDoubleSpending() throws InterruptedException {
        // given
        Long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(50);
        product = productRepository.save(product);
        Long productId = product.getId();

        // 荑좏룿 ?쒗뵆由?諛?諛쒓툒 ?곗씠???명똿
        CouponTemplate template = new CouponTemplate(
                "10% Discount",
                CouponType.RATE,
                new BigDecimal("10"),
                new BigDecimal("20000"),
                new BigDecimal("50000"),
                LocalDateTime.now().plusDays(7)
        );
        template = couponRepository.saveTemplate(template);

        CouponIssue couponIssue = new CouponIssue(userId, template);
        couponIssue = couponRepository.saveIssue(couponIssue);
        Long couponIssueId = couponIssue.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        barrier.await();
                        OrderCheckoutRequest request = new OrderCheckoutRequest(
                                List.of(new OrderCheckoutRequest.Item(productId, 1)),
                                couponIssueId,
                                PaymentMethod.CARD
                        );
                        orderFacade.checkout(userId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(9);

            // 荑좏룿 ?곹깭 USED 諛??숆?????踰꾩쟾 媛깆떊 ?뺤씤
            CouponIssue updatedIssue = couponRepository.findIssueById(couponIssueId).orElseThrow();
            assertThat(updatedIssue.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(updatedIssue.getVersion()).isGreaterThan(0L);
        } finally {
            executorService.shutdown();
        }
    }
}
