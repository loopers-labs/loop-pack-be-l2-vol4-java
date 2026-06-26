package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 실패 보상의 원자성(낙관락) 검증.
 * 같은 주문에 onFailed가 여러 번 동시에 들어와도 OrderModel @Version이 한 번만 CANCELED 전이를 허용해,
 * 비멱등인 재고 복원이 두 번 일어나지 않아야 한다(패자 트랜잭션은 통째로 롤백).
 */
@SpringBootTest
class OrderCompensationConcurrencyIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private OrderPaymentResultHandler handler;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private IssuedCouponRepository issuedCouponRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 주문에 실패 보상이 동시에 여러 번 호출될 시 보상을 처리하면 재고는 한 번만 복원되고 주문은 한 번만 취소된다")
    @Test
    void restoresStockOnce_whenOnFailedConcurrently() throws InterruptedException {
        // given
        stockRepository.save(new StockModel(PRODUCT_ID, 10));
        IssuedCoupon coupon = new IssuedCoupon(USER_ID, 1L);
        coupon.use();
        issuedCouponRepository.save(coupon);
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(PRODUCT_ID, "상품-100", 1_000L, 2)), coupon.getId(), Money.of(500L)));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when - 같은 주문 보상을 동시에 여러 번
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    handler.onFailed(order.getId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // 낙관락 충돌 — 패자 트랜잭션은 재고 복원까지 롤백
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then - 재고는 10 + 2(1회 복원) = 12, 주문 CANCELED, 쿠폰 복원
        assertAll(
            () -> assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED),
            () -> assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(12),
            () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus()).isEqualTo(CouponStatus.AVAILABLE)
        );
    }
}
