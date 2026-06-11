package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.PlaceOrderCommand;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 재고 차감 동시성 검증 — 비관적 락(SELECT ... FOR UPDATE)이 동시 주문을 줄 세워
 * Lost Update 를 차단하고, 재고가 절대 음수로 내려가지 않아야 한다.
 */
@SpringBootTest
class StockConcurrencyTest {

    private static final int STOCK = 10;
    private static final int ATTEMPTS = 15;

    private final OrderFacade orderFacade;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    StockConcurrencyTest(
        OrderFacade orderFacade,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        userJpaRepository.save(new UserModel(
            "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        productId = productJpaRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "운동화", 1000L, STOCK)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 10개 상품에 15건이 동시 주문되면, 정확히 10건만 성공하고 재고는 0이 된다. (음수 불가)")
    @Test
    void stock_neverGoesNegative_underConcurrentOrders() throws InterruptedException {
        // arrange
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ATTEMPTS);

        // act
        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    start.await(); // 동시 출발 보장
                    orderFacade.createOrder("tester01",
                        new PlaceOrderCommand(List.of(new PlaceOrderCommand.Item(productId, 1)), null));
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet(); // 재고 부족 BAD_REQUEST
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        assertAll(
            () -> assertThat(success.get()).isEqualTo(STOCK),
            () -> assertThat(fail.get()).isEqualTo(ATTEMPTS - STOCK),
            () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isZero(),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(STOCK)
        );
    }
}
