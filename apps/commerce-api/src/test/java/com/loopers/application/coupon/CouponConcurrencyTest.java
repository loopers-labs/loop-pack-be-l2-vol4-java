package com.loopers.application.coupon;

import com.loopers.application.order.OrderCriteria;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private static final int THREADS = 10;

    private final List<Long> productIds = new ArrayList<>();
    private Long couponId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        productIds.clear();
        // 상품 경쟁이 끼지 않도록 스레드마다 "다른 상품"(각 재고 1)을 준비
        for (int i = 0; i < THREADS; i++) {
            productIds.add(productRepository.save(
                    new ProductModel(brand.getId(), "상품" + i, null, Money.of(1000L), Quantity.of(1), null)
            ).getId());
        }
        // 같은 쿠폰 1장
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));
        CouponModel template = couponRepository.save(
                CouponModel.create("신규가입 10%", policy, ZonedDateTime.now().plusDays(1)));
        couponId = userCouponRepository.save(template.issue(USER_ID)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 쿠폰으로 여러 주문이 동시에 들어와도, 쿠폰은 단 한 번만 사용된다.")
    @Test
    void sameCoupon_concurrentOrders_usedExactlyOnce() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            final Long productId = productIds.get(i);   // 각자 다른 상품
            executor.submit(() -> {
                try {
                    orderFacade.placeOrder(
                            new OrderCriteria(USER_ID, couponId,
                                    List.of(new OrderCriteria.Line(productId, 1))));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);              // ★ 락 없으면 깨질 지점 (2 이상)
        assertThat(failed.get()).isEqualTo(THREADS - 1);
        assertThat(userCouponRepository.findById(couponId).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.USED);
    }
}
