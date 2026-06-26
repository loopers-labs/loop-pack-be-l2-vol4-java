package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 결제 상태전이 정합성(낙관락) 검증.
 * 콜백·복구가 같은 결제를 동시에 확정해도 PaymentModel @Version이 한 번만 전이를 허용해,
 * 실패 이벤트가 한 번만 발행되고 보상(재고 복원)도 한 번만 일어나야 한다.
 */
@SpringBootTest
class PaymentConfirmConcurrencyIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
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

    @DisplayName("같은 결제에 실패 확정이 동시에 여러 번 요청될 시 확정을 처리하면 재고는 한 번만 복원되고 주문은 한 번만 취소된다")
    @Test
    void compensatesOnce_whenConfirmedConcurrently() throws InterruptedException {
        // given
        stockRepository.save(new StockModel(PRODUCT_ID, 10));
        IssuedCoupon coupon = new IssuedCoupon(USER_ID, 1L);
        coupon.use();
        issuedCouponRepository.save(coupon);
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(PRODUCT_ID, "상품-100", 1_000L, 2)), coupon.getId(), Money.of(500L)));
        PaymentModel payment = new PaymentModel(order.getId(), USER_ID, CardType.SAMSUNG, order.getFinalAmount());
        payment.assignTransactionKey("tx-fail");
        paymentRepository.save(payment);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        // when - 같은 결제를 동시에 실패 확정
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    paymentService.confirm("tx-fail", false, "한도 초과");
                } catch (ObjectOptimisticLockingFailureException ignored) {
                    // 낙관락 충돌 — 승자만 확정·발행, 패자는 예상된 실패
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    unexpected.add(t); // NPE·매핑 오류 등 예상 밖 예외는 테스트를 실패시킨다
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

        // then - 예상 밖 예외 없음 + 재고는 10 + 2(1회 복원) = 12, 주문 CANCELED, 쿠폰 복원
        assertThat(unexpected).isEmpty();
        assertAll(
            () -> assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED),
            () -> assertThat(stockRepository.findByProductId(PRODUCT_ID).orElseThrow().getQuantity()).isEqualTo(12),
            () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus()).isEqualTo(CouponStatus.AVAILABLE)
        );
    }
}
