package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.infrastructure.payment.FakePaymentGateway;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 결제 결과 반영(markPaid/markFailed)의 동시성 — 같은 PENDING 주문이 동시 reconcile/결제 콜백으로
 * 두 번 확정되려 할 때, 상태 전이·보상(재고·쿠폰 원복)이 "정확히 한 번"만 일어나야 한다.
 *
 * 주문 행 비관적 락(OrderRepository.findForUpdate)이 없으면 두 트랜잭션이 모두 PENDING을 읽어
 * requirePending() 가드를 통과 → 재고가 이중 원복(10이 아니라 12)되는 버그가 난다.
 * 락이 있으면 두 번째 트랜잭션이 대기 후 FAILED를 보고 CONFLICT로 떨어져 보상이 한 번만 실행된다.
 */
@SpringBootTest
public class OrderFinalizeConcurrencyTest {

    @Autowired OrderFacade orderFacade;
    @Autowired OrderService orderService;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired StockService stockService;
    @Autowired FakePaymentGateway fakePaymentGateway;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        fakePaymentGateway.reset();
        BrandModel brand = brandService.register("나이키", "스포츠");
        ProductModel product = productService.createProduct(brand.getId(), "에어맥스", "러닝화", null, 10000L);
        productId = product.getId();
        stockService.initialize(productId, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** PG 타임아웃으로 PENDING에 남은 주문 1건(재고 10 → 8). */
    private Long placeTimedOutOrder() {
        fakePaymentGateway.setForcedStatus(PgStatus.TIMEOUT);
        OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                List.of(new OrderLine(productId, 2)));
        assertThat(info.status()).isEqualTo("PENDING");
        return info.id();
    }

    @DisplayName("같은 PENDING 주문을 두 스레드가 동시에 실패 처리해도 재고는 정확히 한 번만 원복된다")
    @Test
    void given_pendingOrder_when_concurrentMarkFailed_then_stockRestoredOnce() throws InterruptedException {
        Long orderId = placeTimedOutOrder();
        assertThat(stockService.getQuantity(productId)).isEqualTo(8);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.markFailed(orderId, "PG 실패");
                    success.incrementAndGet();
                } catch (Throwable t) {
                    conflict.incrementAndGet();   // 두 번째는 CONFLICT (이미 FAILED)
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertAll(
                () -> assertThat(success.get()).isEqualTo(1),
                () -> assertThat(conflict.get()).isEqualTo(1),
                () -> assertThat(orderFacade.getOrder(orderId).status()).isEqualTo("FAILED"),
                // 핵심: 이중 원복이면 12가 된다. 정확히 한 번만 원복돼 10이어야 한다.
                () -> assertThat(stockService.getQuantity(productId)).isEqualTo(10)
        );
    }

    @DisplayName("같은 PENDING 주문을 두 스레드가 동시에 확정(성공)해도 한 건만 PAID로 처리된다")
    @Test
    void given_pendingOrder_when_concurrentMarkPaid_then_paidOnce() throws InterruptedException {
        Long orderId = placeTimedOutOrder();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.markPaid(orderId);
                    success.incrementAndGet();
                } catch (Throwable t) {
                    conflict.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertAll(
                () -> assertThat(success.get()).isEqualTo(1),
                () -> assertThat(conflict.get()).isEqualTo(1),
                () -> assertThat(orderFacade.getOrder(orderId).status()).isEqualTo("PAID"),
                () -> assertThat(stockService.getQuantity(productId)).isEqualTo(8)   // 결제 성공 — 차감 유지
        );
    }
}
