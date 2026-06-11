package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    private static final LocalDateTime VALID_EXPIRED_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct(int stock) {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(stock), brand.getId()));
    }

    private UserCoupon issueFixedCoupon(Long userId, long discountValue, LocalDateTime expiredAt) {
        Coupon coupon = couponJpaRepository.save(new Coupon("정액 할인",
            new Discount(CouponType.FIXED, discountValue), null, expiredAt));
        return userCouponJpaRepository.save(new UserCoupon(userId, coupon.getId()));
    }

    @DisplayName("쿠폰을 적용해 주문할 때, ")
    @Nested
    class PlaceWithCoupon {
        @DisplayName("원금·할인·최종 결제 금액이 스냅샷되고, 쿠폰은 USED 로 전이된다.")
        @Test
        void snapshotsAmountsAndUsesCoupon() {
            // arrange
            Long userId = 1L;
            Product product = saveProduct(10);
            UserCoupon userCoupon = issueFixedCoupon(userId, 1000L, VALID_EXPIRED_AT);

            // act
            OrderInfo info = orderFacade.place(userId,
                List.of(new OrderLineCommand(product.getId(), 3)), userCoupon.getId());

            // assert
            UserCoupon reloadedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(info.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000)),
                () -> assertThat(info.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000)),
                () -> assertThat(info.paymentAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000)),
                () -> assertThat(reloadedCoupon.getStatus()).isEqualTo(CouponStatus.USED)
            );
        }

        @DisplayName("만료된 쿠폰이면 주문이 실패하고, 이미 차감했던 재고도 함께 롤백된다.")
        @Test
        void rollsBackStock_whenCouponIsExpired() {
            // arrange
            Long userId = 1L;
            Product product = saveProduct(10);
            UserCoupon expiredCoupon = issueFixedCoupon(userId, 1000L, LocalDateTime.of(2020, 1, 1, 0, 0));

            // act
            assertThrows(CoreException.class, () -> orderFacade.place(userId,
                List.of(new OrderLineCommand(product.getId(), 3)), expiredCoupon.getId()));

            // assert
            Product reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            UserCoupon reloadedCoupon = userCouponJpaRepository.findById(expiredCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(reloadedProduct.getStock().getQuantity()).isEqualTo(10),
                () -> assertThat(reloadedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(0)
            );
        }
    }

    @DisplayName("동시에 주문할 때, ")
    @Nested
    class ConcurrentPlace {
        @DisplayName("재고 10개 상품에 20명이 1개씩 동시 주문하면, 10건만 성공하고 재고는 0이 되며 음수가 되지 않는다.")
        @Test
        void decreasesStockExactly_whenOrdersCompete() throws Exception {
            // arrange
            Product product = saveProduct(10);
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderFacade.place(userId,
                            List.of(new OrderLineCommand(product.getId(), 1)), null);
                        successCount.incrementAndGet();
                    } catch (Throwable e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(10),
                () -> assertThat(failureCount.get()).isEqualTo(10),
                () -> assertThat(reloaded.getStock().getQuantity()).isEqualTo(0),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(10)
            );
        }

        @DisplayName("동일 쿠폰으로 5건을 동시 주문하면, 1건만 성공하고 쿠폰은 USED 로 한 번만 사용된다.")
        @Test
        void usesCouponOnlyOnce_whenOrdersCompeteWithSameCoupon() throws Exception {
            // arrange
            Long userId = 1L;
            UserCoupon userCoupon = issueFixedCoupon(userId, 1000L, VALID_EXPIRED_AT);
            int threadCount = 5;
            List<Product> products = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                products.add(saveProduct(10));
            }
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // act — 상품을 분리해 재고 락 경합 없이 쿠폰만 경쟁하게 한다
            for (int i = 0; i < threadCount; i++) {
                Product product = products.get(i);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderFacade.place(userId,
                            List.of(new OrderLineCommand(product.getId(), 1)), userCoupon.getId());
                        successCount.incrementAndGet();
                    } catch (Throwable e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            UserCoupon reloadedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(failureCount.get()).isEqualTo(4),
                () -> assertThat(reloadedCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1)
            );
        }
    }
}
