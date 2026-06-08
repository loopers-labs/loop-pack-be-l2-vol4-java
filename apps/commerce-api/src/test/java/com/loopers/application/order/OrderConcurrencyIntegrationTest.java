package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 동시성 통합 테스트.
 *
 * <p>실제 MySQL(Testcontainers) 위에서 락이 동작해야 의미가 있으므로 {@link SpringBootTest} 로 구동한다.
 * 성공 수 / 실패 수 / DB 최종 상태를 모두 검증한다.
 */
@SpringBootTest
class OrderConcurrencyIntegrationTest {

    @Autowired private OrderApplicationService orderApplicationService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel givenProductWithStock(int stockQuantity) {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), stockQuantity));
        return product;
    }

    @DisplayName("재고가 충분하면, 동시 주문이 모두 성공하고 재고도 정확히 차감된다 (비관적 락 정합성).")
    @Test
    void allOrdersSucceed_andStockIsAccurate_whenStockIsSufficient() throws InterruptedException {
        // arrange — 재고 10개, 10명 동시 주문 → 전부 성공하고 재고는 정확히 0
        int threadCount = 10;
        ProductModel product = givenProductWithStock(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    orderApplicationService.createOrder(userId, List.of(new OrderItemCommand(product.getId(), 1)), null);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert — 전부 성공, 재고 정확히 0 (Lost Update 없음)
        StockModel finalStock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(success.get()).isEqualTo(threadCount);
        assertThat(failure.get()).isZero();
        assertThat(finalStock.getQuantity()).isZero();
    }

    @DisplayName("동일 상품에 대해 여러 주문이 동시에 요청되어도, 재고가 정확히 차감된다 (비관적 락).")
    @Test
    void deductsStockExactly_whenConcurrentOrdersOnSameProduct() throws InterruptedException {
        // arrange — 재고 5개, 10명이 1개씩 동시 주문
        int stock = 5;
        int threadCount = 10;
        ProductModel product = givenProductWithStock(stock);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    orderApplicationService.createOrder(userId, List.of(new OrderItemCommand(product.getId(), 1)), null);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert — 정확히 재고 수만큼 성공, 재고는 0, 음수 없음
        StockModel finalStock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(success.get()).isEqualTo(stock);
        assertThat(failure.get()).isEqualTo(threadCount - stock);
        assertThat(finalStock.getQuantity()).isZero();
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한 번만 사용된다 (낙관적 락).")
    @Test
    void usesCouponOnce_whenConcurrentOrdersWithSameCoupon() throws InterruptedException {
        // arrange — 재고 충분, 한 유저가 쿠폰 1장 보유, 같은 쿠폰으로 동시 주문 10건
        int threadCount = 10;
        long userId = 1L;
        ProductModel product = givenProductWithStock(threadCount);   // 재고는 충분히 확보(쿠폰만 경쟁)
        CouponModel coupon = couponRepository.save(
            new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, ZonedDateTime.now().plusDays(1)));
        UserCouponModel userCoupon = userCouponRepository.save(UserCouponModel.issue(userId, coupon));
        Long userCouponId = userCoupon.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderApplicationService.createOrder(userId, List.of(new OrderItemCommand(product.getId(), 1)), userCouponId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert — 쿠폰 사용은 단 1건만 성공, 쿠폰 상태는 USED
        UserCouponModel finalCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threadCount - 1);
        assertThat(finalCoupon.getStatus()).isEqualTo(CouponStatus.USED);
    }
}
