package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
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

/**
 * 주문 Application Layer 동시성 — OrderFacade.placeOrder 경로(주문 생성[tx] + 재고 차감)를
 * 통해 동시 주문이 같은 상품 재고를 깎을 때 lost update 없이 정합성이 보장되는지 검증한다.
 * StockServiceIntegrationTest가 도메인 서비스 단위라면, 이 테스트는 트랜잭션 경계를 포함한 통합 경로를 본다.
 *
 * 재고 차감은 비관적 락(SELECT ... FOR UPDATE)으로 직렬화되므로,
 * 재고 N개에 N보다 많은 동시 주문이 들어와도 정확히 N건만 성공하고 최종 재고는 0(음수 없음)이어야 한다.
 */
@SpringBootTest
public class OrderStockConcurrencyTest {

    @Autowired OrderFacade orderFacade;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired StockService stockService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private static final int INITIAL = 10;
    private static final int THREADS = 20;

    private Long productId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandService.register("나이키", "스포츠");
        ProductModel product = productService.createProduct(brand.getId(), "에어맥스", "러닝화", null, 10000L);
        productId = product.getId();
        stockService.initialize(productId, INITIAL);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 10개에 20건의 주문을 동시에 넣어도 정확히 10건만 성공하고 최종 재고는 0이다")
    @Test
    void given_stock10_when_20concurrentOrders_then_exactly10SucceedAndStockIsZero() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                            List.of(new OrderLine(productId, 1)));
                    success.incrementAndGet();
                } catch (Throwable t) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(INITIAL);
        assertThat(failure.get()).isEqualTo(THREADS - INITIAL);
        assertThat(stockService.getQuantity(productId)).isZero();
    }
}
