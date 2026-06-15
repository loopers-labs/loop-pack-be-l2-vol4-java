package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.*;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
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
    @DisplayName("재고가 5개 있는 상품에 대해 10명의 사용자가 동시에 checkout을 요청하면 5명만 성공하고 5명은 실패한다.")
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

            // 남은 재고 확인
            ProductModel updatedProduct = productRepository.findById(productId).orElseThrow();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(0);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("동일한 쿠폰을 사용하여 10개의 스레드에서 동시에 checkout을 요청하면 1개만 성공하고 나머지는 실패한다. (Double Spending 방어)")
    void checkout_ConcurrentCouponUse_ShouldPreventDoubleSpending() throws InterruptedException {
        // given
        Long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(50);
        product = productRepository.save(product);
        Long productId = product.getId();

        // 쿠폰 템플릿 및 발급 데이터 세팅
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

            // 쿠폰 상태 USED 및 낙관적 락 버전 갱신 확인
            CouponIssue updatedIssue = couponRepository.findIssueById(couponIssueId).orElseThrow();
            assertThat(updatedIssue.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(updatedIssue.getVersion()).isGreaterThan(0L);
        } finally {
            executorService.shutdown();
        }
    }
}
