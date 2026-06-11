package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserName;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 주문 흐름(OrderFacade.placeOrder) 단위의 동시성 검증.
 * 같은 쿠폰으로 동시에 여러 번 주문해도 쿠폰은 한 번만 사용되고 주문도 한 건만 성공해야 한다.
 */
@SpringBootTest
class OrderConcurrencyIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long couponId;

    @BeforeEach
    void setUp() {
        UserModel user = userRepository.save(new UserModel(
            new LoginId("loopers01"), "$2a$10$dummyEncodedHash",
            new UserName("홍길동"), LocalDate.of(2002, 5, 11), new Email("test@loopers.com")
        ));
        userId = user.getId();

        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L));
        productId = product.getId();
        stockRepository.save(new StockModel(productId, 100));

        CouponTemplate template = couponTemplateRepository.save(
            new CouponTemplate("3천원 할인", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(7)));
        couponId = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId())).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 여러 주문이 동시에 요청되어도 쿠폰은 한 번만 사용되고 주문도 한 건만 성공한다")
    @Test
    void onlyOneOrderSucceeds_whenSameCouponUsedConcurrently() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // when - 같은 쿠폰으로 동시에 주문
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderFacade.placeOrder(userId, List.of(new OrderLineCommand(productId, 1)), couponId);
                    success.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    failure.incrementAndGet();
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

        // then - 주문 1건만 성공, 쿠폰 1회 USED, 재고도 1개만 차감
        assertAll(
            () -> assertThat(success.get()).isEqualTo(1),
            () -> assertThat(failure.get()).isEqualTo(threadCount - 1),
            () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
            () -> assertThat(issuedCouponRepository.findById(couponId).orElseThrow().getStatus()).isEqualTo(CouponStatus.USED),
            () -> assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(99)
        );
    }
}
