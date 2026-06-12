package com.loopers.concurrency;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.order.application.OrderCommand;
import com.loopers.order.application.PlaceOrderFacade;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderDeadlockPreventionTest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final int STOCK = 50;
    // 주문 1건이 커넥션 2개(주문 TX + 채번 REQUIRES_NEW)를 점유하므로, test 풀(10) 고갈을 피해 4로 둔다.
    private static final int THREADS = 4;

    @Autowired private PlaceOrderFacade placeOrderFacade;
    @Autowired private UserAccountService userAccountService;
    @Autowired private BrandAdminService brandAdminService;
    @Autowired private ProductAdminService productAdminService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productAId;
    private Long productBId;

    @BeforeEach
    void setUp() {
        userId = userAccountService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        )).id();
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productAId = productAdminService.create(new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, null, STOCK)).id();
        productBId = productAdminService.create(new ProductCommand.Create(brandId, "바지", "설명", 39_000L, null, STOCK)).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("여러 스레드가 역순 상품 조합으로 동시에 주문해도 데드락 없이 모두 성공한다")
    void givenReversedProductOrders_whenConcurrentPlace_thenNoDeadlock() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            // 절반은 [A, B], 절반은 [B, A] 순서로 요청해 락 획득 순서가 엇갈리는 상황을 만든다.
            boolean reversed = i % 2 == 1;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    placeOrderFacade.place(order(reversed));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        int stockA = productAdminService.getProduct(productAId).stockQuantity();
        int stockB = productAdminService.getProduct(productBId).stockQuantity();
        assertAll(
                () -> assertThat(finished).as("데드락 대기 없이 제한 시간 내 완료").isTrue(),
                () -> assertThat(success.get()).isEqualTo(THREADS),
                () -> assertThat(failure.get()).isZero(),
                () -> assertThat(stockA).isEqualTo(STOCK - THREADS),
                () -> assertThat(stockB).isEqualTo(STOCK - THREADS)
        );
    }

    private OrderCommand.Create order(boolean reversed) {
        List<OrderCommand.Line> lines = reversed
                ? List.of(new OrderCommand.Line(productBId, 1), new OrderCommand.Line(productAId, 1))
                : List.of(new OrderCommand.Line(productAId, 1), new OrderCommand.Line(productBId, 1));
        return new OrderCommand.Create(
                userId, lines,
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동",
                null
        );
    }
}
