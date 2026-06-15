package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        savedProduct = productJpaRepository.save(new ProductModel(brand, "나이키 에어맥스", 150_000));
        stockJpaRepository.save(new StockModel(savedProduct, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderCreateCommand commandWith(int quantity) {
        return new OrderCreateCommand(USER_ID,
            List.of(new OrderItemCommand(savedProduct.getId(), quantity)), null);
    }

    private OrderCreateCommand commandWithCoupon(int quantity, Long couponId) {
        return new OrderCreateCommand(USER_ID,
            List.of(new OrderItemCommand(savedProduct.getId(), quantity)), couponId);
    }

    private UserCouponModel saveUserCoupon(Long userId, CouponType type, int value) {
        CouponModel coupon = couponJpaRepository.save(
            new CouponModel("테스트쿠폰", type, value, null, FUTURE));
        return userCouponJpaRepository.save(new UserCouponModel(userId, coupon));
    }

    @DisplayName("createOrder()를 호출할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 주문 생성 시 DB에 저장되고 OrderInfo가 반환된다.")
        @Test
        void savesOrderAndReturnsInfo_whenValidCommandProvided() {
            OrderInfo result = orderFacade.createOrder(commandWith(2));

            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(result.totalAmount()).isEqualTo(300_000),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(orderJpaRepository.findById(result.id())).isPresent()
            );
        }

        @DisplayName("주문 후 재고가 차감된다.")
        @Test
        void decreasesStock_afterOrderCreated() {
            orderFacade.createOrder(commandWith(3));

            int remaining = stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity();
            assertThat(remaining).isEqualTo(7);
        }

        @DisplayName("재고를 초과하는 수량으로 주문 시 BAD_REQUEST 예외가 발생하고 재고가 변하지 않는다.")
        @Test
        void throwsBadRequest_andStockUnchanged_whenQuantityExceedsStock() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(commandWith(999))
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity()).isEqualTo(10);
        }

        @DisplayName("존재하지 않는 상품으로 주문 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            OrderCreateCommand command = new OrderCreateCommand(USER_ID,
                List.of(new OrderItemCommand(999L, 1)), null);

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(), null);

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("중복된 productId가 포함된 주문 시 BAD_REQUEST 예외가 발생하고 재고가 변하지 않는다.")
        @Test
        void throwsBadRequest_andStockUnchanged_whenDuplicateProductIdProvided() {
            OrderCreateCommand command = new OrderCreateCommand(USER_ID,
                List.of(
                    new OrderItemCommand(savedProduct.getId(), 1),
                    new OrderItemCommand(savedProduct.getId(), 2)
                ), null);

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity()).isEqualTo(10);
        }

        @Transactional
        @DisplayName("재고 차감 JPQL에 qty=0을 넘기면 차감되지 않고 재고가 유지된다.")
        @Test
        void stockUnchanged_whenDecreaseQuantityCalledWithZero() {
            int affected = stockJpaRepository.decreaseQuantity(savedProduct.getId(), 0);

            assertThat(affected).isEqualTo(0);
            assertThat(stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity()).isEqualTo(10);
        }

        @Transactional
        @DisplayName("재고 차감 JPQL에 qty=-1을 넘기면 차감되지 않고 재고가 유지된다.")
        @Test
        void stockUnchanged_whenDecreaseQuantityCalledWithNegative() {
            int affected = stockJpaRepository.decreaseQuantity(savedProduct.getId(), -1);

            assertThat(affected).isEqualTo(0);
            assertThat(stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity()).isEqualTo(10);
        }

        @DisplayName("정액 쿠폰 적용 시 할인 금액만큼 차감된 totalAmount가 반환된다.")
        @Test
        void appliesFixedCouponDiscount_whenValidCouponProvided() {
            // arrange — 상품 1개(150,000원), 정액 10,000원 쿠폰
            UserCouponModel userCoupon = saveUserCoupon(USER_ID, CouponType.FIXED, 10_000);

            // act
            OrderInfo result = orderFacade.createOrder(commandWithCoupon(1, userCoupon.getId()));

            // assert
            assertAll(
                () -> assertThat(result.originalAmount()).isEqualTo(150_000),
                () -> assertThat(result.discountAmount()).isEqualTo(10_000),
                () -> assertThat(result.totalAmount()).isEqualTo(140_000)
            );
        }

        @DisplayName("정률 쿠폰 적용 시 비율에 따라 할인된 totalAmount가 반환된다.")
        @Test
        void appliesRateCouponDiscount_whenValidCouponProvided() {
            // arrange — 상품 1개(150,000원), 정률 10% 쿠폰
            UserCouponModel userCoupon = saveUserCoupon(USER_ID, CouponType.RATE, 10);

            // act
            OrderInfo result = orderFacade.createOrder(commandWithCoupon(1, userCoupon.getId()));

            // assert
            assertAll(
                () -> assertThat(result.originalAmount()).isEqualTo(150_000),
                () -> assertThat(result.discountAmount()).isEqualTo(15_000),
                () -> assertThat(result.totalAmount()).isEqualTo(135_000)
            );
        }

        @DisplayName("쿠폰 적용 주문 성공 시 해당 쿠폰의 status가 USED로 변경된다.")
        @Test
        void marksCouponAsUsed_afterOrderWithCouponSucceeds() {
            // arrange
            UserCouponModel userCoupon = saveUserCoupon(USER_ID, CouponType.RATE, 10);

            // act
            orderFacade.createOrder(commandWithCoupon(1, userCoupon.getId()));

            // assert
            UserCouponModel result = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertThat(result.getStatus().name()).isEqualTo("USED");
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(commandWithCoupon(1, 999L)));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인 소유의 쿠폰으로 주문 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponBelongsToOtherUser() {
            // arrange — 다른 유저(99L) 소유의 쿠폰
            UserCouponModel othersCoupon = saveUserCoupon(99L, CouponType.RATE, 10);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(commandWithCoupon(1, othersCoupon.getId())));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            // arrange — 쿠폰을 먼저 사용 처리
            UserCouponModel userCoupon = saveUserCoupon(USER_ID, CouponType.RATE, 10);
            userCoupon.use();
            userCouponJpaRepository.save(userCoupon);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(commandWithCoupon(1, userCoupon.getId())));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰으로 주문 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // arrange — 만료된 쿠폰을 직접 저장
            CouponModel expiredCoupon = couponJpaRepository.save(
                new CouponModel("만료쿠폰", CouponType.RATE, 10, null, PAST));
            UserCouponModel userCoupon = userCouponJpaRepository.save(
                new UserCouponModel(USER_ID, expiredCoupon));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(commandWithCoupon(1, userCoupon.getId())));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("getMyOrders()를 호출할 때,")
    @Nested
    class GetMyOrders {

        @DisplayName("본인 주문 목록이 페이지로 반환된다.")
        @Test
        void returnsMyOrders_whenOrdersExist() {
            orderFacade.createOrder(commandWith(1));
            orderFacade.createOrder(commandWith(2));

            Page<OrderInfo> result = orderFacade.getMyOrders(USER_ID, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("타인의 주문은 포함되지 않는다.")
        @Test
        void excludesOtherUsersOrders() {
            orderFacade.createOrder(commandWith(1));
            OrderCreateCommand otherCommand = new OrderCreateCommand(99L,
                List.of(new OrderItemCommand(savedProduct.getId(), 1)), null);
            orderFacade.createOrder(otherCommand);

            Page<OrderInfo> result = orderFacade.getMyOrders(USER_ID, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @DisplayName("getOrder()를 호출할 때,")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 상세 조회 시 OrderInfo가 반환된다.")
        @Test
        void returnsOrderInfo_whenOrderBelongsToUser() {
            OrderInfo created = orderFacade.createOrder(commandWith(1));

            OrderInfo result = orderFacade.getOrder(USER_ID, created.id());

            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("타인의 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderBelongsToOtherUser() {
            OrderInfo created = orderFacade.createOrder(commandWith(1));

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.getOrder(99L, created.id())
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.getOrder(USER_ID, 999L)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
