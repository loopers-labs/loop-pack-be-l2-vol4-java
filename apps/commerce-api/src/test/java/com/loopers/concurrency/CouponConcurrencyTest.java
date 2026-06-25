package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.PlaceOrderCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 쿠폰 1회 사용 동시성 검증 — 스레드마다 "다른 상품"을 주문해 상품 비관적 락의
 * 직렬화 효과를 제거하고, UserCoupon 의 @Version 낙관적 락이 직접 1명만
 * 성공시키는지 검증한다. (실패 트랜잭션의 재고 차감은 전부 롤백되어야 한다)
 */
@SpringBootTest
class CouponConcurrencyTest {

    private static final int ATTEMPTS = 10;
    private static final int STOCK_PER_PRODUCT = 10;

    private final OrderFacade orderFacade;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private List<Long> productIds;
    private Long userCouponId;

    @Autowired
    CouponConcurrencyTest(
        OrderFacade orderFacade,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        User user = userJpaRepository.save(new User(
            "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));

        productIds = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            productIds.add(productJpaRepository.save(
                new Product(brand.getId(), "상품" + i, "설명", 10000L, STOCK_PER_PRODUCT)).getId());
        }

        Coupon coupon = couponJpaRepository.save(
            new Coupon("10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30)));
        UserCoupon userCoupon = userCouponJpaRepository.save(
            coupon.issueTo(user.getId(), ZonedDateTime.now()));
        this.userCouponId = userCoupon.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 쿠폰으로 10건이 동시 주문되어도, 단 1건만 성공하고 쿠폰은 정확히 1회만 사용된다.")
    @Test
    void coupon_usedExactlyOnce_underConcurrentOrders() throws InterruptedException {
        // arrange
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ATTEMPTS);

        // act — 스레드마다 다른 상품 + 같은 쿠폰
        for (int i = 0; i < ATTEMPTS; i++) {
            final Long productId = productIds.get(i);
            executor.submit(() -> {
                try {
                    start.await(); // 동시 출발 보장
                    orderFacade.createOrder("tester01",
                        new PlaceOrderCommand(List.of(new PlaceOrderCommand.Item(productId, 1)), userCouponId));
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet(); // 낙관적 락 충돌(OptimisticLockingFailure) 또는 이미 사용(BAD_REQUEST)
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        long totalRemainingStock = productJpaRepository.findAll().stream()
            .mapToLong(Product::getStock)
            .sum();
        assertAll(
            () -> assertThat(success.get()).isEqualTo(1),
            () -> assertThat(fail.get()).isEqualTo(ATTEMPTS - 1),
            () -> assertThat(userCouponJpaRepository.findById(userCouponId).orElseThrow().getStatus())
                .isEqualTo(UserCouponStatus.USED),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(1L),
            // 실패한 9건의 재고 차감은 전부 롤백 — 성공 1건만 반영되어야 한다
            () -> assertThat(totalRemainingStock).isEqualTo((long) ATTEMPTS * STOCK_PER_PRODUCT - 1)
        );
    }
}
