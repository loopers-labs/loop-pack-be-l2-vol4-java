package com.loopers.concurrency;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.order.OrderCreationService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 동시성 테스트.
 * <p>
 * 같은 사용자의 같은 쿠폰으로 N 개 기기에서 동시에 주문 → 정확히 1 번만 성공.
 * <p>
 * 검증 대상: UserCoupon 비관적 락(PESSIMISTIC_WRITE) + status AVAILABLE→USED 전이.
 */
class CouponConcurrencyTest extends AbstractH2ConcurrencyTest {

    @Autowired
    private CouponTemplateService couponTemplateService;
    @Autowired
    private UserCouponService userCouponService;
    @Autowired
    private UserCouponRepository userCouponRepository;
    @Autowired
    private OrderCreationService orderCreationService;
    @Autowired
    private ProductService productService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private com.loopers.utils.DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 10 개 기기에서 동시에 주문해도, 쿠폰은 단 한 번만 사용된다.")
    @Test
    void couponUsedExactlyOnce() throws InterruptedException {
        // arrange — 충분한 재고 + 발급된 쿠폰
        Long brandId = brandService.createBrand("나이키", "스포츠").getId();
        ProductModel product = productService.createProduct(brandId, "에어맥스", "런닝화", 1000L, 9999, null);
        long productId = product.getId();
        long userId = 100L;

        CouponTemplate template = couponTemplateService.create(
            "1000원 할인", CouponType.FIXED, 100L, 0L, LocalDateTime.of(2099, 12, 31, 23, 59));
        UserCoupon issued = userCouponService.issue(userId, template.getId());
        long couponId = issued.getId();

        int threadCount = 10;

        // act — 같은 쿠폰을 10 개 기기에서 동시 주문 시도
        ConcurrencyTestSupport.Result result = ConcurrencyTestSupport.runRace(threadCount, () ->
            orderCreationService.create(userId, List.of(new OrderLine(productId, 1)), couponId)
        );

        // assert — 정확히 1 건 성공, 나머지 9 건은 CONFLICT 로 실패
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(threadCount - 1);

        UserCoupon refreshed = userCouponRepository.find(couponId).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(UserCouponStatus.USED);
    }
}
