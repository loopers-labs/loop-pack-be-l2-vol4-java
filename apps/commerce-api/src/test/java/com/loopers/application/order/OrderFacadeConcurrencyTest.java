package com.loopers.application.order;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderFacadeConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 동시에 주문을 요청하면, 1건만 성공하고 나머지는 실패한다.")
    @Test
    void onlyOneOrderSucceeds_whenSameCouponUsedConcurrently() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(
            new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), 100L));

        CouponEntity coupon = couponJpaRepository.save(
            new CouponEntity("10% 할인 쿠폰", CouponType.RATE, BigDecimal.TEN, null, ZonedDateTime.now().plusDays(30))
        );
        IssuedCouponEntity issuedCoupon = issuedCouponJpaRepository.save(new IssuedCouponEntity(coupon.getId(), 1L, ZonedDateTime.now().plusDays(30)));

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    OrderCommand.Create command = new OrderCommand.Create(1L, issuedCoupon.getId(), List.of(
                        new OrderCommand.Create.Item(product.getId(), 1)
                    ));
                    orderFacade.createOrder(command);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        IssuedCouponEntity result = issuedCouponJpaRepository.findById(issuedCoupon.getId()).orElseThrow();
        assertAll(
            () -> assertThat(successCount.get()).isEqualTo(1),
            () -> assertThat(failCount.get()).isEqualTo(threadCount - 1),
            () -> assertThat(result.getStatus()).isEqualTo(CouponStatus.USED),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(1)
        );
    }

    @DisplayName("재고가 부족한 상품을 동시에 주문하면, 재고 수량만큼만 주문이 성공한다.")
    @Test
    void onlyStockCountOrdersSucceed_whenSameProductOrderedConcurrently() throws InterruptedException {
        // Arrange
        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(
            new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
        int initialStock = 10;
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), (long) initialStock));

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    OrderCommand.Create command = new OrderCommand.Create(userId, null, List.of(
                        new OrderCommand.Create.Item(product.getId(), 1)
                    ));
                    orderFacade.createOrder(command);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        ProductStockEntity stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertAll(
            () -> assertThat(successCount.get()).isEqualTo(initialStock),
            () -> assertThat(failCount.get()).isEqualTo(threadCount - initialStock),
            () -> assertThat(stock.getQuantity()).isEqualTo(0L),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(initialStock)
        );
    }
}
