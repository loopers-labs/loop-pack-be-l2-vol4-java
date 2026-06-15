package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private Long shoesId;  // 단가 1000, 재고 10
    private Long sockId;   // 단가 2000, 재고 3

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        shoesId = productRepository.save(
                new ProductModel(brand.getId(), "운동화", null, Money.of(1000L), Quantity.of(10), null)).getId();
        sockId = productRepository.save(
                new ProductModel(brand.getId(), "양말", null, Money.of(2000L), Quantity.of(3), null)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderCriteria.Line line(Long productId, int qty) {
        return new OrderCriteria.Line(productId, qty);
    }

    /** 템플릿 생성 → 유저에게 발급 → 발급쿠폰 id 반환 */
    private Long issueCoupon(Long userId, DiscountPolicy policy, ZonedDateTime expiredAt) {
        CouponModel template = couponRepository.save(CouponModel.create("테스트쿠폰", policy, expiredAt));
        return userCouponRepository.save(template.issue(userId)).getId();
    }

    @DisplayName("여러 상품을 주문하면 총액은 소계 합이고, 각 상품 재고가 차감되며, 상태는 PENDING 이다.")
    @Test
    void placeOrder_calculatesTotalAndDecreasesStock() {
        // 1000×2 + 2000×1 = 4000
        OrderInfo info = orderFacade.placeOrder(
                new OrderCriteria(USER_ID, List.of(line(shoesId, 2), line(sockId, 1))));

        assertThat(info.totalAmount()).isEqualTo(4000L);
        assertThat(info.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(info.items()).hasSize(2);
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(8));
        assertThat(productRepository.findById(sockId).get().getStockQuantity()).isEqualTo(Quantity.of(2));
    }

    @DisplayName("한 상품이라도 재고가 부족하면 BAD_REQUEST 이고, 앞서 차감된 다른 상품 재고도 전부 롤백된다. (All-or-Nothing)")
    @Test
    void placeOrder_rollsBackAll_whenAnyStockInsufficient() {
        // 운동화 2개(가능) + 양말 5개(재고 3 → 부족) → 전체 실패
        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(
                        new OrderCriteria(USER_ID, List.of(line(shoesId, 2), line(sockId, 5)))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        // 운동화는 먼저 차감됐지만 롤백되어 원래 재고 10 그대로
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(10));
        assertThat(productRepository.findById(sockId).get().getStockQuantity()).isEqualTo(Quantity.of(3));
    }

    @DisplayName("존재하지 않는 상품을 주문하면 NOT_FOUND.")
    @Test
    void placeOrder_throwsNotFound_whenProductDoesNotExist() {
        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(
                        new OrderCriteria(USER_ID, List.of(line(999_999L, 1)))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("주문 항목이 비어있으면 BAD_REQUEST.")
    @Test
    void placeOrder_throwsBadRequest_whenItemsEmpty() {
        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(new OrderCriteria(USER_ID, List.of())));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("유효한 쿠폰으로 주문하면 할인이 적용되고, 재고가 차감되며, 쿠폰은 USED 가 된다.")
    @Test
    void placeOrder_appliesCoupon_decreasesStockAndMarksCouponUsed() {
        // 10% 정률, 최소금액 0, 내일 만료
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));
        Long couponId = issueCoupon(USER_ID, policy, ZonedDateTime.now().plusDays(1));

        // 운동화 2개 = 2000 → 10% 할인 200 → 최종 1800
        OrderInfo info = orderFacade.placeOrder(
                new OrderCriteria(USER_ID, couponId, List.of(line(shoesId, 2))));

        assertThat(info.totalAmount()).isEqualTo(2000L);
        assertThat(info.discountAmount()).isEqualTo(200L);
        assertThat(info.finalAmount()).isEqualTo(1800L);
        assertThat(info.userCouponId()).isEqualTo(couponId);
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(8));
        assertThat(userCouponRepository.findById(couponId).get().getStatus()).isEqualTo(CouponStatus.USED);
    }

    @DisplayName("이미 사용된 쿠폰으로 주문하면 CONFLICT 이고, 앞서 차감된 재고도 전부 롤백된다.")
    @Test
    void placeOrder_rollsBackStock_whenCouponAlreadyUsed() {
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));
        Long couponId = issueCoupon(USER_ID, policy, ZonedDateTime.now().plusDays(1));
        // 미리 사용 처리
        UserCouponModel used = userCouponRepository.findById(couponId).get();
        used.use(ZonedDateTime.now());
        userCouponRepository.save(used);

        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(
                        new OrderCriteria(USER_ID, couponId, List.of(line(shoesId, 2)))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        // 재고 차감이 롤백되어 원래 10 그대로
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(10));
    }

    @DisplayName("타인 소유 쿠폰으로 주문하면 BAD_REQUEST 이고, 재고도 롤백된다.")
    @Test
    void placeOrder_rollsBackStock_whenCouponBelongsToAnotherUser() {
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));
        Long otherUsersCoupon = issueCoupon(999L, policy, ZonedDateTime.now().plusDays(1));

        CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(
                        new OrderCriteria(USER_ID, otherUsersCoupon, List.of(line(shoesId, 2)))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(productRepository.findById(shoesId).get().getStockQuantity()).isEqualTo(Quantity.of(10));
    }
}
