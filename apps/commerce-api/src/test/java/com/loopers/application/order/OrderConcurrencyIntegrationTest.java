package com.loopers.application.order;

import com.loopers.application.payment.FakePgGateway;
import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.payment.CardType;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문-결제 동시성 통합 테스트.
 *
 * <p>무점유 주문 설계에서 동시성 경합 지점은 주문 생성이 아니라 <strong>결제 요청 시점의
 * 자원 점유(조건부 원자 UPDATE 차감 + 쿠폰 낙관적 락)</strong>다. 따라서 각 스레드는
 * 주문 생성 → 결제 요청 풀 플로우를 수행하고, 점유 단계의 정합성을 검증한다.
 * 외부 PG 응답은 {@link FakePgGateway}(항상 성공)로 고정해 경합 지점만 분리한다.
 * 실제 MySQL(Testcontainers) 위에서 동작해야 의미가 있으므로 {@link SpringBootTest} 로 구동한다.
 */
@SpringBootTest
@Import(OrderConcurrencyIntegrationTest.FakeGatewayConfig.class)
class OrderConcurrencyIntegrationTest {

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        FakePgGateway fakePgGateway() {
            return new FakePgGateway();
        }
    }

    @Autowired private OrderApplicationService orderApplicationService;
    @Autowired private PaymentApplicationService paymentApplicationService;
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

    /** 주문 생성 → 결제 요청(자원 점유) 풀 플로우. 점유까지 성공하면 success, 경합 탈락은 failure. */
    private void orderAndPay(Long userId, Long productId, Long couponId,
                             AtomicInteger success, AtomicInteger failure) {
        try {
            OrderInfo pending = orderApplicationService.createOrder(
                userId, List.of(new OrderItemCommand(productId, 1)), couponId);
            paymentApplicationService.requestPayment(
                userId, pending.id(), CardType.SAMSUNG, "1234-5678-9012-3456", pending.totalPrice());
            success.incrementAndGet();
        } catch (Exception e) {
            failure.incrementAndGet();
        }
    }

    @DisplayName("재고가 충분하면, 동시 주문-결제가 모두 성공하고 재고도 정확히 차감된다 (원자적 차감 정합성).")
    @Test
    void allOrdersSucceed_andStockIsAccurate_whenStockIsSufficient() throws InterruptedException {
        // arrange — 재고 10개, 10명 동시 주문-결제 → 전부 점유 성공하고 재고는 정확히 0
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
                    orderAndPay(userId, product.getId(), null, success, failure);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 전부 성공, 재고 정확히 0 (Lost Update 없음)
        StockModel finalStock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(success.get()).isEqualTo(threadCount);
        assertThat(failure.get()).isZero();
        assertThat(finalStock.getQuantity()).isZero();
    }

    @DisplayName("재고보다 많은 동시 주문-결제가 들어와도, 재고 수만큼만 성공한다 (조건부 원자 UPDATE).")
    @Test
    void deductsStockExactly_whenConcurrentOrdersExceedStock() throws InterruptedException {
        // arrange — 재고 5개, 10명이 1개씩. 무점유 견적이라 주문 생성은 10명 전부 통과하고,
        //           점유 시점의 조건부 차감(WHERE quantity >= ?)이 정확히 5명만 통과시켜야 한다
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
                    orderAndPay(userId, product.getId(), null, success, failure);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 정확히 재고 수만큼 성공, 재고는 0, 음수 없음. 탈락자는 점유 단계에서 거부됨
        StockModel finalStock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(success.get()).isEqualTo(stock);
        assertThat(failure.get()).isEqualTo(threadCount - stock);
        assertThat(finalStock.getQuantity()).isZero();
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문-결제해도, 쿠폰은 단 한 번만 사용된다 (낙관적 락).")
    @Test
    void usesCouponOnce_whenConcurrentOrdersWithSameCoupon() throws InterruptedException {
        // arrange — 재고 충분, 한 유저가 쿠폰 1장 보유. 견적(주문 생성)은 10건 모두 가능하지만
        //           점유 시점의 쿠폰 확정(낙관적 락)은 한 건만 통과해야 한다
        int threadCount = 10;
        long userId = 1L;
        ProductModel product = givenProductWithStock(threadCount);
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
                    orderAndPay(userId, product.getId(), userCouponId, success, failure);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 점유 성공은 단 1건, 쿠폰 상태는 USED, 재고도 1개만 차감 (탈락 건의 점유는 롤백)
        UserCouponModel finalCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
        StockModel finalStock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threadCount - 1);
        assertThat(finalCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(finalStock.getQuantity()).isEqualTo(threadCount - 1);
    }
}
