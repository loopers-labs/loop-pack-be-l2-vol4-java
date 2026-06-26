package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
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
    @Autowired OrderService orderService;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired CouponService couponService;
    @Autowired UserCouponService userCouponService;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired StockService stockService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;
    private Long productId;

    @BeforeEach
    void setUp() {
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
        return issueRateCouponTo(USER_ID, percent, minOrderAmount);
    }

    private Long issueRateCouponTo(Long userId, int percent, Long minOrderAmount) {
        CouponModel template = couponService.register("할인", CouponType.RATE, percent, minOrderAmount,
                ZonedDateTime.now().plusDays(7));
        userCouponService.issue(userId, template.getId());
        return template.getId();
    }

    private int stock() {
        return stockService.getQuantity(productId);
    }

    @Nested
    @DisplayName("UC-17 쿠폰을 적용한 주문 (성공)")
    class Success {

        @DisplayName("할인이 반영된 최종 금액으로 PENDING 주문이 생성되고, 쿠폰 사용·금액 3종 스냅샷이 남는다")
        @Test
        void given_coupon_when_placeOrder_then_discountedPending() {
            Long couponId = issueRateCoupon(10, 5000L);

            OrderInfo info = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), couponId);   // 20000원

            assertAll(
                    () -> assertThat(info.status()).isEqualTo("PENDING"),
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

        @DisplayName("이미 사용된 쿠폰으로 다시 주문하면 NOT_FOUND이고 재고가 원복된다")
        @Test
        void given_alreadyUsedCoupon_when_placeOrder_then_notFoundAndRolledBack() {
            Long couponId = issueRateCoupon(10, null);
            orderFacade.placeOrder(USER_ID, PaymentMethod.CARD, List.of(new OrderLine(productId, 2)), couponId);  // 1회 소진

            Throwable thrown = catchThrowable(() -> orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), couponId));

            assertAll(
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(stock()).isEqualTo(8)   // 첫 주문 차감만 반영(10→8), 둘째 주문 차감은 롤백
            );
        }

        @DisplayName("만료된 쿠폰으로 주문하면 BAD_REQUEST이고 재고가 원복된다")
        @Test
        void given_expiredCoupon_when_placeOrder_then_badRequestAndRolledBack() {
            CouponModel template = couponService.register("할인", CouponType.RATE, 10L, null,
                    ZonedDateTime.now().plusDays(7));
            userCouponService.issue(USER_ID, template.getId());                       // 유효할 때 발급
            couponService.update(template.getId(), "할인", 10L, null,
                    ZonedDateTime.now().minusDays(1));                                // 이후 만료시킴

            Throwable thrown = catchThrowable(() -> orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), template.getId()));

            assertAll(
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> assertThat(stock()).isEqualTo(10)   // 차감 롤백
            );
        }

        @DisplayName("타 유저 소유 쿠폰으로 주문하면 NOT_FOUND이고 재고가 원복된다")
        @Test
        void given_otherUsersCoupon_when_placeOrder_then_notFoundAndRolledBack() {
            Long couponId = issueRateCouponTo(200L, 10, null);   // 다른 유저에게만 발급

            Throwable thrown = catchThrowable(() -> orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), couponId));

            assertAll(
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(stock()).isEqualTo(10)   // 차감 롤백
            );
        }
    }

    @Nested
    @DisplayName("UC-19 결제 실패 (재고 + 쿠폰 원복)")
    class PaymentFailure {

        @DisplayName("PENDING 주문이 결제 실패(markFailed)로 확정되면 재고와 쿠폰이 모두 원복된다")
        @Test
        void given_pendingOrder_when_markFailed_then_stockAndCouponRestored() {
            Long couponId = issueRateCoupon(10, null);
            OrderInfo placed = orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(productId, 2)), couponId);
            assertThat(placed.status()).isEqualTo("PENDING");

            orderService.markFailed(placed.id(), "결제 거절");

            OrderInfo info = orderFacade.getOrder(placed.id());
            assertAll(
                    () -> assertThat(info.status()).isEqualTo("FAILED"),
                    () -> assertThat(info.discountAmount()).isEqualTo(2000L),   // 스냅샷엔 남음
                    () -> assertThat(stock()).isEqualTo(10),                     // 재고 원복
                    () -> assertThat(userCouponRepository.findFirstAvailable(USER_ID, couponId)).isPresent()  // 쿠폰 원복
            );
        }
    }
}
