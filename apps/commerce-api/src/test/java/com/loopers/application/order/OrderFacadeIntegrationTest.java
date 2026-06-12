package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private CouponService couponService;

    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CouponTemplateJpaRepository couponTemplateJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private MemberModel member;
    private ProductModel product;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("브랜드", "brand01", "b@test.com"));
        member = memberJpaRepository.save(
                new MemberModel("user1", "Password1!", "u@test.com", "김테스트", "19940101"));
        product = productJpaRepository.save(
                new ProductModel(brand.getId(), "상품", "설명", 10000L, 10, null));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 성공 시,")
    @Nested
    class PlaceOrderSuccess {

        @DisplayName("재고가 차감되고, 주문이 저장된다.")
        @Test
        void deducts_stock_and_saves_order() {
            // act
            OrderInfo result = orderFacade.placeOrder(member.getId(),
                    List.of(new OrderItemRequest(product.getId(), 3)),
                    null);

            // assert
            ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(updated.getStock()).isEqualTo(7),
                    () -> assertThat(result.totalAmount()).isEqualTo(30000L),
                    () -> assertThat(result.discountAmount()).isEqualTo(0L),
                    () -> assertThat(orderJpaRepository.findById(result.id())).isPresent()
            );
        }

        @DisplayName("쿠폰을 적용하면 쿠폰이 USED 상태가 되고 할인 금액이 반영된다.")
        @Test
        void applies_coupon_and_marks_it_used() {
            // arrange
            CouponTemplate template = couponTemplateJpaRepository.save(
                    new CouponTemplate("1000원 할인", CouponType.FIXED, 1000L, null,
                            ZonedDateTime.now().plusDays(30)));
            UserCoupon userCoupon = userCouponJpaRepository.save(new UserCoupon(member.getId(), template));

            // act
            OrderInfo result = orderFacade.placeOrder(member.getId(),
                    List.of(new OrderItemRequest(product.getId(), 1)),
                    userCoupon.getId());

            // assert
            UserCoupon usedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(usedCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                    () -> assertThat(result.originalAmount()).isEqualTo(10000L),
                    () -> assertThat(result.discountAmount()).isEqualTo(1000L),
                    () -> assertThat(result.totalAmount()).isEqualTo(9000L),
                    () -> assertThat(result.couponId()).isEqualTo(userCoupon.getId())
            );
        }
    }

    @DisplayName("주문 실패 시 롤백 검증 —")
    @Nested
    class PlaceOrderRollback {

        @DisplayName("재고가 부족하면 주문이 실패하고, 재고는 변경되지 않는다.")
        @Test
        void rolls_back_when_stock_is_insufficient() {
            // act
            assertThrows(CoreException.class, () ->
                    orderFacade.placeOrder(member.getId(),
                            List.of(new OrderItemRequest(product.getId(), 999)),
                            null));

            // assert — 재고 원복 확인
            ProductModel unchanged = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(unchanged.getStock()).isEqualTo(10);
            assertThat(orderJpaRepository.count()).isEqualTo(0);
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문하면 실패하고, 재고는 롤백된다.")
        @Test
        void rolls_back_stock_when_coupon_not_found() {
            // act
            assertThrows(CoreException.class, () ->
                    orderFacade.placeOrder(member.getId(),
                            List.of(new OrderItemRequest(product.getId(), 1)),
                            999L));

            // assert — 재고 차감 후 쿠폰 실패 → 재고 롤백
            ProductModel unchanged = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(unchanged.getStock()).isEqualTo(10);
            assertThat(orderJpaRepository.count()).isEqualTo(0);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 실패하고, 재고는 롤백된다.")
        @Test
        void rolls_back_stock_when_coupon_already_used() {
            // arrange
            CouponTemplate template = couponTemplateJpaRepository.save(
                    new CouponTemplate("1000원 할인", CouponType.FIXED, 1000L, null,
                            ZonedDateTime.now().plusDays(30)));
            UserCoupon userCoupon = userCouponJpaRepository.save(new UserCoupon(member.getId(), template));
            userCoupon.use();
            userCouponJpaRepository.save(userCoupon);

            // act
            assertThrows(CoreException.class, () ->
                    orderFacade.placeOrder(member.getId(),
                            List.of(new OrderItemRequest(product.getId(), 1)),
                            userCoupon.getId()));

            // assert
            ProductModel unchanged = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(unchanged.getStock()).isEqualTo(10);
            assertThat(orderJpaRepository.count()).isEqualTo(0);
        }
    }
}
