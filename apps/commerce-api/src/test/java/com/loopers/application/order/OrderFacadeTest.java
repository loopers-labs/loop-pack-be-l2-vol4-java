package com.loopers.application.order;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.FakeCouponRepository;
import com.loopers.domain.coupon.FakeUserCouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.FakeOrderRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.FakeProductRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 주문 유스케이스(Application Layer) 단위 테스트.
 * Fake 저장소를 주입해 상품 조회 → (쿠폰 적용) → 재고 차감 → 저장 흐름과 예외 흐름을 검증한다.
 *
 * Fake 저장소 환경에서는 실제 @Transactional 트랜잭션이 동작하지 않으므로,
 * "쿠폰 무효 시 전체 롤백" 의 원자성 검증은 통합/E2E 레이어에서 수행하고,
 * 여기서는 예외 발생 시점 이후 동작이 진행되지 않는지(재고 차감/주문 저장 미발생)까지만 검증한다.
 */
class OrderFacadeTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59);

    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        orderRepository = new FakeOrderRepository();
        couponRepository = new FakeCouponRepository();
        userCouponRepository = new FakeUserCouponRepository();
        ProductService productService = new ProductService(productRepository);
        OrderService orderService = new OrderService();
        CouponService couponService = new CouponService(couponRepository, userCouponRepository);
        orderFacade = new OrderFacade(orderService, productService, couponService, orderRepository);
    }

    private Product saveProduct(String name, long price, int stock) {
        return productRepository.save(Product.create(name, "설명", Money.of(price), stock, 1L));
    }

    private UserCoupon issueCoupon(Long userId, CouponType type, long value, LocalDateTime expiredAt) {
        Coupon coupon = couponRepository.save(Coupon.create("쿠폰", type, value, null, expiredAt));
        return userCouponRepository.save(UserCoupon.issue(userId, coupon.getId()));
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("쿠폰 미적용 정상 주문이면, 재고가 차감되고 원금=최종 금액인 주문이 저장된다.")
        @Test
        void createsAndSavesOrder_whenNoCoupon() {
            // arrange
            Product productA = saveProduct("상품A", 1_500L, 10);
            Product productB = saveProduct("상품B", 1_000L, 5);
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(productA.getId(), 2),
                new OrderItemCommand(productB.getId(), 1)
            );

            // act
            OrderInfo result = orderFacade.createOrder(USER_ID, items, null);

            // assert
            assertThat(result.orderId()).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.originalAmount()).isEqualTo(4_000L);
            assertThat(result.discountAmount()).isEqualTo(0L);
            assertThat(result.finalAmount()).isEqualTo(4_000L);
            assertThat(result.userCouponId()).isNull();
            assertThat(result.items()).hasSize(2);
            assertThat(productRepository.find(productA.getId()).orElseThrow().getStock()).isEqualTo(8);
            assertThat(productRepository.find(productB.getId()).orElseThrow().getStock()).isEqualTo(4);
            assertThat(orderRepository.find(result.orderId())).isPresent();
        }

        @DisplayName("쿠폰을 적용한 정상 주문이면, 할인이 반영되고 UserCoupon 이 USED 로 전이된다.")
        @Test
        void createsOrderWithDiscount_andMarksCouponUsed() {
            // arrange
            Product product = saveProduct("상품A", 1_500L, 10);
            UserCoupon issued = issueCoupon(USER_ID, CouponType.FIXED, 1_000L, FAR_FUTURE);
            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 2));

            // act
            OrderInfo result = orderFacade.createOrder(USER_ID, items, issued.getId());

            // assert
            assertThat(result.originalAmount()).isEqualTo(3_000L);
            assertThat(result.discountAmount()).isEqualTo(1_000L);
            assertThat(result.finalAmount()).isEqualTo(2_000L);
            assertThat(result.userCouponId()).isEqualTo(issued.getId());
            assertThat(userCouponRepository.find(issued.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.USED);
            assertThat(productRepository.find(product.getId()).orElseThrow().getStock()).isEqualTo(8);
        }

        @DisplayName("타 유저의 쿠폰을 사용하려 하면, NOT_FOUND 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_andDoesNotSave_whenCouponOwnedByOther() {
            // arrange
            Product product = saveProduct("상품A", 1_000L, 10);
            UserCoupon othersCoupon = issueCoupon(999L, CouponType.FIXED, 500L, FAR_FUTURE);
            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 1));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, items, othersCoupon.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(orderRepository.findByUserId(USER_ID)).isEmpty();
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면, CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_andDoesNotSave_whenCouponAlreadyUsed() {
            // arrange
            Product product = saveProduct("상품A", 2_000L, 10);
            UserCoupon issued = issueCoupon(USER_ID, CouponType.FIXED, 500L, FAR_FUTURE);
            // 첫 주문으로 쿠폰을 USED 로 만든다
            orderFacade.createOrder(USER_ID, List.of(new OrderItemCommand(product.getId(), 1)), issued.getId());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, List.of(new OrderItemCommand(product.getId(), 1)), issued.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            // 두 번째 주문은 저장되지 않아 총 주문 수는 1개
            assertThat(orderRepository.findByUserId(USER_ID)).hasSize(1);
        }

        @DisplayName("존재하지 않는 상품을 주문하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            List<OrderItemCommand> items = List.of(new OrderItemCommand(999L, 1));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, items, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면, CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_andDoesNotSave_whenStockIsNotEnough() {
            // arrange
            Product product = saveProduct("상품A", 1_000L, 1);
            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 2));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, items, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(orderRepository.findByUserId(USER_ID)).isEmpty();
        }

        @DisplayName("로그인 유저 정보가 없으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenUserIsNull() {
            Product product = saveProduct("상품A", 1_000L, 10);
            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 1));

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(null, items, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, List.of(), null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
