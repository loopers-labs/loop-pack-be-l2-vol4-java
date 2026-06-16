package com.loopers.concurrency;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.coupon.application.CouponAdminService;
import com.loopers.coupon.application.CouponCommand;
import com.loopers.coupon.application.CouponIssueService;
import com.loopers.coupon.application.CouponQueryService;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.order.application.OrderCommand;
import com.loopers.order.application.PlaceOrderFacade;
import com.loopers.order.domain.OrderRepository;
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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final int THREADS = 10;

    @Autowired private PlaceOrderFacade placeOrderFacade;
    @Autowired private UserAccountService userAccountService;
    @Autowired private BrandAdminService brandAdminService;
    @Autowired private ProductAdminService productAdminService;
    @Autowired private CouponAdminService couponAdminService;
    @Autowired private CouponIssueService couponIssueService;
    @Autowired private CouponQueryService couponQueryService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long userCouponId;

    @BeforeEach
    void setUp() {
        userId = userAccountService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        )).id();
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, null, 50)).id();
        Long couponTemplateId = couponAdminService.create(
                new CouponCommand.Create("3천원 할인", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(30))
        ).id();
        userCouponId = couponIssueService.issue(userId, couponTemplateId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일한 쿠폰으로 여러 주문이 동시에 들어와도 쿠폰은 단 한 번만 사용된다")
    void givenSameCoupon_whenConcurrentOrders_thenCouponUsedOnce() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    placeOrderFacade.place(order());
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
        done.await();
        pool.shutdown();

        CouponStatus couponStatus = couponQueryService.getMyCoupons(userId).coupons().get(0).status();
        int stock = productAdminService.getProduct(productId).stockQuantity();
        assertAll(
                () -> assertThat(success.get()).isEqualTo(1),
                () -> assertThat(failure.get()).isEqualTo(THREADS - 1),
                () -> assertThat(couponStatus).isEqualTo(CouponStatus.USED),
                () -> assertThat(orderRepository.findByUserId(userId)).hasSize(1),
                () -> assertThat(stock).isEqualTo(49)
        );
    }

    private OrderCommand.Create order() {
        return new OrderCommand.Create(
                userId, List.of(new OrderCommand.Line(productId, 1)),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동",
                userCouponId
        );
    }
}
