package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private OrderFacade orderFacade;

    private ProductModel product(int price) {
        return product(price, 50);
    }

    private ProductModel product(int price, int stock) {
        return ProductModel.builder()
            .brandId(1L)
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(stock)
            .build();
    }

    private BrandModel brand() {
        return BrandModel.builder()
            .rawName("감성 브랜드")
            .rawDescription("감성을 담은 브랜드")
            .build();
    }

    private OrderItemModel orderItem(Long productId, int unitPrice, int quantity) {
        return OrderItemModel.builder()
            .productId(productId)
            .productName("감성 가디건")
            .productBrandName("감성 브랜드")
            .unitPrice(unitPrice)
            .rawQuantity(quantity)
            .build();
    }

    private OrderModel order(Long userId, int originalAmount) {
        return OrderModel.builder()
            .userId(userId)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(originalAmount)
            .discountAmount(0)
            .finalAmount(originalAmount)
            .build();
    }

    @DisplayName("단건 주문을 생성할 때,")
    @Nested
    class CreateSingleOrder {

        private final Long userId = 1L;
        private final Long productId = 10L;

        @DisplayName("재고가 충분하면 재고를 차감하고 스냅샷을 기록한 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenStockIsSufficient() {
            // arrange
            ProductModel product = product(39_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveByIdForUpdate(productId)).willReturn(product);
            given(brandRepository.getActiveById(product.getBrandId())).willReturn(brand());
            given(orderRepository.save(any(OrderModel.class), anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderInfo orderInfo = orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now());

            // assert
            assertAll(
                () -> assertThat(orderInfo.items()).hasSize(1),
                () -> assertThat(orderInfo.items().get(0).productName()).isEqualTo("감성 가디건"),
                () -> assertThat(orderInfo.items().get(0).brandName()).isEqualTo("감성 브랜드"),
                () -> assertThat(orderInfo.items().get(0).unitPrice()).isEqualTo(39_000),
                () -> assertThat(orderInfo.items().get(0).quantity()).isEqualTo(2),
                () -> assertThat(orderInfo.originalAmount()).isEqualTo(78_000),
                () -> assertThat(orderInfo.discountAmount()).isZero(),
                () -> assertThat(orderInfo.finalAmount()).isEqualTo(78_000),
                () -> assertThat(orderInfo.userCouponId()).isNull(),
                () -> assertThat(product.getStock().value()).isEqualTo(48),
                () -> then(orderRepository).should().save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("주문 회원이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파되고 재고 차감·저장이 일어나지 않는다.")
        @Test
        void throwsNotFound_whenUserIsAbsent() {
            // arrange
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userRepository.getActiveById(userId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(productRepository).should(never()).getActiveByIdForUpdate(anyLong()),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("대상 상품이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파되고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(productRepository.getActiveByIdForUpdate(productId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("재고가 요청 수량에 미치지 못하면 CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_whenStockIsInsufficient() {
            // arrange
            ProductModel product = product(39_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 100));
            given(productRepository.getActiveByIdForUpdate(productId)).willReturn(product);
            given(brandRepository.getActiveById(product.getBrandId())).willReturn(brand());

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("수량이 1 미만이면 BAD_REQUEST 예외가 발생하고 재고 차감·저장이 일어나지 않는다.")
        @Test
        void throwsBadRequest_whenQuantityIsLessThanOne() {
            // arrange
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 0));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> then(productRepository).should(never()).getActiveByIdForUpdate(anyLong()),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }
    }

    @DisplayName("다중 항목 주문을 생성할 때,")
    @Nested
    class CreateMultiOrder {

        private final Long userId = 1L;
        private final Long firstProductId = 10L;
        private final Long secondProductId = 20L;

        @DisplayName("모든 항목의 재고가 충분하면 항목별 스냅샷을 기록하고 총액을 합산한 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenAllItemsSucceed() {
            // arrange
            ProductModel firstProduct = product(10_000);
            ProductModel secondProduct = product(5_000);
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(secondProductId, 2));
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveByIdForUpdate(firstProductId)).willReturn(firstProduct);
            given(productRepository.getActiveByIdForUpdate(secondProductId)).willReturn(secondProduct);
            given(brandRepository.getActiveById(firstProduct.getBrandId())).willReturn(brand());
            given(orderRepository.save(any(OrderModel.class), anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderInfo orderInfo = orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now());

            // assert
            assertAll(
                () -> assertThat(orderInfo.items()).hasSize(2),
                () -> assertThat(orderInfo.originalAmount()).isEqualTo(20_000),
                () -> then(orderRepository).should().save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("같은 상품 식별자가 둘 이상 포함되면 BAD_REQUEST 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsBadRequest_whenProductIsDuplicated() {
            // arrange
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(firstProductId, 2));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("어느 한 항목의 상품이 없으면 NOT_FOUND 예외가 전파되고 저장하지 않는다.")
        @Test
        void throwsNotFound_whenAnyItemProductIsAbsent() {
            // arrange
            ProductModel firstProduct = product(10_000);
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(secondProductId, 2));
            given(productRepository.getActiveByIdForUpdate(firstProductId)).willReturn(firstProduct);
            given(brandRepository.getActiveById(firstProduct.getBrandId())).willReturn(brand());
            given(productRepository.getActiveByIdForUpdate(secondProductId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("어느 한 항목의 재고가 부족하면 CONFLICT 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsConflict_whenAnyItemStockIsInsufficient() {
            // arrange
            ProductModel firstProduct = product(10_000);
            ProductModel secondProduct = product(5_000, 3);
            List<OrderItemCommand> itemCommands =
                List.of(new OrderItemCommand(firstProductId, 1), new OrderItemCommand(secondProductId, 5));
            given(productRepository.getActiveByIdForUpdate(firstProductId)).willReturn(firstProduct);
            given(productRepository.getActiveByIdForUpdate(secondProductId)).willReturn(secondProduct);
            given(brandRepository.getActiveById(firstProduct.getBrandId())).willReturn(brand());

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, null, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }
    }

    @DisplayName("쿠폰을 적용해 주문을 생성할 때,")
    @Nested
    class CreateOrderWithCoupon {

        private final Long userId = 1L;
        private final Long productId = 10L;
        private final Long userCouponId = 50L;
        private final ZonedDateTime now = ZonedDateTime.now();

        private UserCouponModel userCoupon(DiscountType type, int value, Integer minOrderAmount) {
            CouponModel coupon = CouponModel.builder()
                .rawName("할인 쿠폰")
                .type(type)
                .rawValue(value)
                .rawMinOrderAmount(minOrderAmount)
                .rawExpiredAt(now.plusDays(7))
                .now(now)
                .build();
            ReflectionTestUtils.setField(coupon, "id", 1L);

            return UserCouponModel.issue(userId, coupon);
        }

        private UserCouponModel usedCoupon() {
            return UserCouponModel.builder()
                .userId(userId)
                .couponId(1L)
                .name("할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5_000)
                .minOrderAmount(10_000)
                .expiredAt(now.plusDays(7))
                .usedAt(now.minusDays(1))
                .build();
        }

        private void givenSufficientStock(int price) {
            ProductModel product = product(price);
            UserModel user = mock(UserModel.class);
            given(user.getId()).willReturn(userId);
            given(userRepository.getActiveById(userId)).willReturn(user);
            given(productRepository.getActiveByIdForUpdate(productId)).willReturn(product);
            given(brandRepository.getActiveById(product.getBrandId())).willReturn(brand());
        }

        @DisplayName("본인 소유·사용 가능 쿠폰이면 할인 금액을 계산하고 쿠폰을 사용 완료로 전이한 뒤 세 금액을 기록한다.")
        @Test
        void appliesCoupon_andTransitionsToUsed() {
            // arrange (78,000원 주문 + 정액 5,000원 쿠폰)
            givenSufficientStock(39_000);
            UserCouponModel userCoupon = userCoupon(DiscountType.FIXED, 5_000, 10_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userCouponRepository.getActiveByIdAndUserId(userCouponId, userId)).willReturn(userCoupon);
            given(orderRepository.save(any(OrderModel.class), anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            OrderInfo orderInfo = orderFacade.createOrder(userId, itemCommands, userCouponId, now);

            // assert
            assertAll(
                () -> assertThat(orderInfo.originalAmount()).isEqualTo(78_000),
                () -> assertThat(orderInfo.discountAmount()).isEqualTo(5_000),
                () -> assertThat(orderInfo.finalAmount()).isEqualTo(73_000),
                () -> assertThat(orderInfo.userCouponId()).isEqualTo(userCouponId),
                () -> assertThat(userCoupon.getUsedAt()).isEqualTo(now),
                () -> then(orderRepository).should().save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("쿠폰이 없거나 타인 소유면 NOT_FOUND 예외가 전파되고 주문은 저장되지 않는다.")
        @Test
        void throwsNotFound_whenCouponIsAbsentOrNotOwned() {
            // arrange
            givenSufficientStock(39_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userCouponRepository.getActiveByIdAndUserId(userCouponId, userId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "발급 쿠폰이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, userCouponId, now))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("이미 사용한 쿠폰이면 CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_whenCouponIsAlreadyUsed() {
            // arrange
            givenSufficientStock(39_000);
            UserCouponModel usedCoupon = usedCoupon();
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userCouponRepository.getActiveByIdAndUserId(userCouponId, userId)).willReturn(usedCoupon);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, userCouponId, now))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("만료된 쿠폰이면 CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_whenCouponIsExpired() {
            // arrange (과거 기준 시각으로 만들어 실제로는 만료된 쿠폰)
            givenSufficientStock(39_000);
            ZonedDateTime pastExpiredAt = now.minusDays(1);
            CouponModel expiredTemplate = CouponModel.builder()
                .rawName("만료 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(pastExpiredAt)
                .now(pastExpiredAt.minusDays(1))
                .build();
            ReflectionTestUtils.setField(expiredTemplate, "id", 1L);
            UserCouponModel expiredCoupon = UserCouponModel.issue(userId, expiredTemplate);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userCouponRepository.getActiveByIdAndUserId(userCouponId, userId)).willReturn(expiredCoupon);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, userCouponId, now))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("할인 전 주문 금액이 최소 주문 금액에 미치지 못하면 CONFLICT 예외가 발생하고 쿠폰은 사용되지 않는다.")
        @Test
        void throwsConflict_whenOrderAmountIsBelowMinimum() {
            // arrange (78,000원 주문 + 최소 100,000원 쿠폰)
            givenSufficientStock(39_000);
            UserCouponModel userCoupon = userCoupon(DiscountType.FIXED, 5_000, 100_000);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userCouponRepository.getActiveByIdAndUserId(userCouponId, userId)).willReturn(userCoupon);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, userCouponId, now))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(userCoupon.getUsedAt()).isNull(),
                () -> then(orderRepository).should(never()).save(any(OrderModel.class), anyList())
            );
        }

        @DisplayName("재고 차감에 실패하면 쿠폰 검증·사용에 도달하지 않는다.")
        @Test
        void doesNotTouchCoupon_whenStockIsInsufficient() {
            // arrange
            ProductModel product = product(39_000, 1);
            List<OrderItemCommand> itemCommands = List.of(new OrderItemCommand(productId, 2));
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveByIdForUpdate(productId)).willReturn(product);
            given(brandRepository.getActiveById(product.getBrandId())).willReturn(brand());

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(userId, itemCommands, userCouponId, now))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(userCouponRepository).should(never()).getActiveByIdAndUserId(anyLong(), anyLong())
            );
        }
    }

    @DisplayName("본인 주문 상세를 조회할 때,")
    @Nested
    class ReadMyOrder {

        private final Long authUserId = 1L;
        private final Long orderId = 100L;

        @DisplayName("본인 주문이면 항목을 포함한 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenOwnedByUser() {
            // arrange
            OrderModel order = order(authUserId, 78_000);
            given(orderRepository.getActiveByIdAndUserId(orderId, authUserId)).willReturn(order);
            given(orderRepository.findActiveItemsByOrderId(order.getId())).willReturn(List.of(orderItem(10L, 39_000, 2)));

            // act
            OrderInfo orderInfo = orderFacade.readMyOrder(authUserId, orderId);

            // assert
            assertAll(
                () -> assertThat(orderInfo.items()).hasSize(1),
                () -> assertThat(orderInfo.finalAmount()).isEqualTo(78_000)
            );
        }

        @DisplayName("주문이 없거나 타인 소유면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAbsentOrNotOwned() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(orderId, authUserId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.readMyOrder(authUserId, orderId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("본인 주문 내역을 날짜 범위로 조회할 때,")
    @Nested
    class ReadMyOrders {

        private final Long userId = 1L;

        @DisplayName("시작일·종료일이 없으면 오늘 기준 한 달 전부터 오늘까지의 범위로 조회한다.")
        @Test
        void appliesDefaultRange_whenDatesAreAbsent() {
            // arrange
            LocalDate today = LocalDate.now();
            ZonedDateTime expectedStart = today.minusMonths(1).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime expectedEndExclusive = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());
            given(orderRepository.findActiveByUserIdAndOrderedAtBetween(userId, expectedStart, expectedEndExclusive, 0, 20))
                .willReturn(Page.empty());

            // act
            orderFacade.readMyOrders(userId, null, null, 0, 20);

            // assert
            then(orderRepository).should().findActiveByUserIdAndOrderedAtBetween(userId, expectedStart, expectedEndExclusive, 0, 20);
        }

        @DisplayName("시작일이 종료일보다 늦으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStartIsAfterEnd() {
            // arrange
            LocalDate startAt = LocalDate.now();
            LocalDate endAt = startAt.minusDays(1);

            // act & assert
            assertThatThrownBy(() -> orderFacade.readMyOrders(userId, startAt, endAt, 0, 20))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("시작일이 오늘 이후이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStartIsAfterToday() {
            // arrange
            LocalDate startAt = LocalDate.now().plusDays(1);
            LocalDate endAt = LocalDate.now().plusDays(2);

            // act & assert
            assertThatThrownBy(() -> orderFacade.readMyOrders(userId, startAt, endAt, 0, 20))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("관리자 주문 목록을 조회할 때,")
    @Nested
    class ReadOrders {

        @DisplayName("전체 주문을 헤더 레벨 정보 페이지로 변환해 반환한다.")
        @Test
        void returnsAdminSummaryInfoPage() {
            // arrange
            OrderModel order = order(7L, 78_000);
            given(orderRepository.findActiveByPage(0, 20)).willReturn(new PageImpl<>(List.of(order)));

            // act
            Page<OrderAdminSummaryInfo> result = orderFacade.readOrders(0, 20);

            // assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(1),
                () -> assertThat(result.getContent().get(0).userId()).isEqualTo(7L),
                () -> assertThat(result.getContent().get(0).finalAmount()).isEqualTo(78_000)
            );
        }
    }

    @DisplayName("관리자 주문 상세를 조회할 때,")
    @Nested
    class ReadOrder {

        private final Long orderId = 100L;

        @DisplayName("존재하는 주문이면 회원 식별자와 항목을 포함한 상세를 반환한다.")
        @Test
        void returnsAdminInfo_whenOrderExists() {
            // arrange
            OrderModel order = order(7L, 78_000);
            given(orderRepository.getActiveById(orderId)).willReturn(order);
            given(orderRepository.findActiveItemsByOrderId(order.getId())).willReturn(List.of(orderItem(10L, 39_000, 2)));

            // act
            OrderAdminInfo orderAdminInfo = orderFacade.readOrder(orderId);

            // assert
            assertAll(
                () -> assertThat(orderAdminInfo.userId()).isEqualTo(7L),
                () -> assertThat(orderAdminInfo.items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면 NOT_FOUND 예외가 전파된다.")
        @Test
        void throwsNotFound_whenOrderIsAbsent() {
            // arrange
            given(orderRepository.getActiveById(orderId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.readOrder(orderId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
