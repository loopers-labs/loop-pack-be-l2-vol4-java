package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.application.user.UserService;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private UserService userService;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;
    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        savedUser = userService.signUp(new UserModel(
            "user01", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "user@example.com"
        ));
        savedProduct = productJpaRepository.save(new ProductModel("에어포스1", 10000L, 1L));
        stockJpaRepository.save(new StockModel(savedProduct.getId(), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("CC-1: 동일 쿠폰으로 2개 스레드가 동시에 주문 생성 시 1개만 성공한다.")
    @Test
    void concurrentOrderWithSameCoupon_onlyOneSucceeds() throws InterruptedException {
        // arrange
        CouponTemplateModel template = couponTemplateJpaRepository.save(
            new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));
        UserCouponModel userCoupon = userCouponJpaRepository.save(
            new UserCouponModel(savedUser.getId(), template.getId()));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                    orderFacade.createOrder(
                        savedUser.getId(),
                        List.of(new OrderItemCommand(savedProduct.getId(), 1)),
                        userCoupon.getId()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // assert
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }

    @DisplayName("CC-2: 동일 회원이 동일 쿠폰 템플릿을 동시에 발급 요청 시 1개만 성공한다.")
    @Test
    void concurrentCouponIssue_onlyOneSucceeds() throws InterruptedException {
        // arrange
        CouponTemplateModel template = couponTemplateJpaRepository.save(
            new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                    couponFacade.issue(savedUser.getId(), template.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // assert
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }
}
