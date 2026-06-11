package com.loopers.application.order;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
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
class OrderConcurrencyTest {

    private final OrderFacade orderFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final CouponService couponService;
    private final OrderJpaRepository orderJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final StockJpaRepository stockJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private static final ZonedDateTime FAR_FUTURE = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");

    private Long userId;
    private Long productId;
    private Long userCouponId;

    @Autowired
    public OrderConcurrencyTest(
        OrderFacade orderFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        CouponService couponService,
        OrderJpaRepository orderJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        StockJpaRepository stockJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.couponService = couponService;
        this.orderJpaRepository = orderJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 재고 경합을 배제하기 위해 재고를 넉넉히 둔다 (쿠폰 단일사용만 격리해 검증).
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 1_000L, 200, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
        )).id();
        Long policyId = couponService.createPolicy("정액 100원", CouponType.FIXED, 100L, null, FAR_FUTURE).getId();
        userCouponId = couponService.issue(userId, policyId).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 쿠폰으로 한 사용자가 N 개의 주문을 동시에 보내도, 정확히 1 건만 성공하고 쿠폰은 USED 1 회로만 전이된다 (이중사용 0).")
    @Test
    void usesCouponExactlyOnce_whenSameCouponIsOrderedConcurrently() throws InterruptedException {
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
                        List.of(new OrderCommand.Line(productId, 1)), userCouponId));
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
            () -> assertThat(success.get()).isEqualTo(1),
            () -> assertThat(failures).hasSize(threadCount - 1),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(1L),
            () -> assertThat(userCouponJpaRepository.findById(userCouponId).orElseThrow().getStatus())
                .isEqualTo(UserCouponStatus.USED),
            () -> assertThat(stockJpaRepository.findByProductId(productId).orElseThrow().getQuantity())
                .isEqualTo(199),
            () -> assertThat(failures).allSatisfy(e ->
                assertThat(e).isInstanceOfAny(ObjectOptimisticLockingFailureException.class, CoreException.class))
        );
    }
}
