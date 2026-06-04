package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.infrastructure.payment.FakePaymentGateway;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class OrderCouponIntegrationTest {

    @Autowired OrderFacade orderFacade;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired CouponService couponService;
    @Autowired UserCouponService userCouponService;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired StockService stockService;
    @Autowired FakePaymentGateway fakePaymentGateway;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
        fakePaymentGateway.reset();
        BrandModel brand = brandService.register("나이키", "스포츠");
        ProductModel product = productService.createProduct(brand.getId(), "에어맥스", "러닝화", null, 10000L);
        productId = product.getId();
        stockService.initialize(productId, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long issueRateCoupon(int percent, Long minOrderAmount) {
        CouponModel template = couponService.register("할인", CouponType.RATE, percent, minOrderAmount,
                ZonedDateTime.now().plusDays(7));
        userCouponService.issue(USER_ID, template.getId());
        return template.getId();
    }

    private int stock() {
        return stockService.getQuantity(productId);
    }

    @Nested
    @DisplayName("UC-17 쿠폰을 적용한 주문 (성공)")
    class Success {

        @DisplayName("할인이 반영되어 최종 금액으로 결제되고, 금액 3종이 스냅샷으로 남는다")
        @Test
        void given_couponAndPgSuccess_when_placeOrder_then_discountedPaid() {
            Long couponId = issueRateCoupon(10, 5000L);
            fakePaymentGateway.setForcedStatus(PgStatus.SUCCESS);

            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), couponId);   // 20000원

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("PAID"),
                    () -> assertThat(info.totalAmount()).isEqualTo(20000L),
                    () -> assertThat(info.discountAmount()).isEqualTo(2000L),
                    () -> assertThat(info.finalAmount()).isEqualTo(18000L),
                    () -> assertThat(info.userCouponId()).isNotNull(),
                    () -> assertThat(userCouponRepository.findFirstAvailable(USER_ID, couponId)).isEmpty()
            );
        }
    }

    @Nested
    @DisplayName("UC-18 잘못된 쿠폰 (주문 실패)")
    class InvalidCoupon {

        @DisplayName("보유하지 않은 쿠폰으로 주문하면 NOT_FOUND이고 재고가 원복된다")
        @Test
        void given_notOwnedCoupon_when_placeOrder_then_notFoundAndRolledBack() {
            CouponModel template = couponService.register("할인", CouponType.RATE, 10L, null,
                    ZonedDateTime.now().plusDays(7));   // 발급하지 않음

            Throwable thrown = catchThrowable(() -> orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), template.getId()));

            assertAll(
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(stock()).isEqualTo(10)   // 차감 롤백
            );
        }
    }

    @Nested
    @DisplayName("UC-19 결제 실패 (재고 + 쿠폰 원복)")
    class PaymentFailure {

        @DisplayName("PG가 실패하면 재고와 쿠폰이 모두 원복되고 주문은 FAILED가 된다")
        @Test
        void given_pgFailed_when_placeOrder_then_stockAndCouponRestored() {
            Long couponId = issueRateCoupon(10, null);
            fakePaymentGateway.setForcedStatus(PgStatus.FAILED);

            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), couponId);

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("FAILED"),
                    () -> assertThat(info.discountAmount()).isEqualTo(2000L),   // 스냅샷엔 남음
                    () -> assertThat(stock()).isEqualTo(10),                     // 재고 원복
                    () -> assertThat(userCouponRepository.findFirstAvailable(USER_ID, couponId)).isPresent()  // 쿠폰 원복
            );
        }
    }
}
