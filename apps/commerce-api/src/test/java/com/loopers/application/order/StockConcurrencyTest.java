package com.loopers.application.order;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StockConcurrencyTest {

    private final OrderFacade orderFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final StockJpaRepository stockJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private static final int INITIAL_STOCK = 30;

    private Long userId;
    private Long productId;
    private Long brandId;

    @Autowired
    public StockConcurrencyTest(
        OrderFacade orderFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderJpaRepository orderJpaRepository,
        StockJpaRepository stockJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 1_000L, INITIAL_STOCK, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 N 인 상품에 N+k 개의 주문이 동시에 들어와도, 성공 주문은 정확히 N 건이고 재고는 정확히 0 이다 (과판매 0).")
    @Test
    void decreasesStockExactlyToZeroWithoutOversell_whenConcurrentOrdersExceedStock() throws InterruptedException {
        // given
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        List<Exception> failures = new CopyOnWriteArrayList<>();

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderFacade.placeOrder(userId, new OrderCommand.Place(
                        List.of(new OrderCommand.Line(productId, 1))));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertAll(
            () -> assertThat(success.get()).isEqualTo(INITIAL_STOCK),
            () -> assertThat(orderJpaRepository.count()).isEqualTo((long) INITIAL_STOCK),
            () -> assertThat(stockJpaRepository.findByProductId(productId).orElseThrow().getQuantity())
                .isEqualTo(0),
            () -> assertThat(failures).hasSize(threadCount - INITIAL_STOCK),
            () -> assertThat(failures).allSatisfy(e -> {
                assertThat(e).isInstanceOf(CoreException.class);
                assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK);
            })
        );
    }

    @DisplayName("서로 다른 두 상품을 반대 순서로 동시에 다항목 주문해도, 데드락 없이 전부 성공하고 두 재고가 정확히 차감된다.")
    @Test
    void avoidsDeadlockAndDecrementsConsistently_whenMultiItemOrdersLockInReverseOrder() throws InterruptedException {
        // given
        int stock = 100;
        int threadCount = 40;
        Long productAId = productFacade.createProduct("상품 A", "설명", 1_000L, stock, brandId).id();
        Long productBId = productFacade.createProduct("상품 B", "설명", 2_000L, stock, brandId).id();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        List<Exception> failures = new CopyOnWriteArrayList<>();

        // when
        for (int i = 0; i < threadCount; i++) {
            // 절반은 [A, B], 절반은 [B, A] 순서로 주문해 락 획득 순서가 엇갈리도록 한다.
            List<OrderCommand.Line> items = (i % 2 == 0)
                ? List.of(new OrderCommand.Line(productAId, 1), new OrderCommand.Line(productBId, 1))
                : List.of(new OrderCommand.Line(productBId, 1), new OrderCommand.Line(productAId, 1));
            executor.submit(() -> {
                try {
                    orderFacade.placeOrder(userId, new OrderCommand.Place(items));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertAll(
            () -> assertThat(success.get()).isEqualTo(threadCount),
            () -> assertThat(failures).isEmpty(),
            () -> assertThat(stockJpaRepository.findByProductId(productAId).orElseThrow().getQuantity())
                .isEqualTo(stock - threadCount),
            () -> assertThat(stockJpaRepository.findByProductId(productBId).orElseThrow().getQuantity())
                .isEqualTo(stock - threadCount)
        );
    }
}
