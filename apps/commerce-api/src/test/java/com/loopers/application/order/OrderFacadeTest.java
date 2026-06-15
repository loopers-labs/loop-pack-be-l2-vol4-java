package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.loopers.domain.coupon.UserCouponRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @InjectMocks
    private OrderFacade orderFacade;

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;
    @Mock private OrderDomainService orderDomainService;
    @Mock private UserCouponRepository userCouponRepository;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private ProductModel product;

    @BeforeEach
    void setUp() {
        BrandModel brand = new BrandModel("Nike", "스포츠 브랜드");
        product = new ProductModel(brand, "나이키 에어맥스", 150_000);
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    }

    @DisplayName("createOrder()를 호출할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 주문 생성 시 재고를 원자적으로 차감하고 OrderInfo가 반환된다.")
        @Test
        void returnsOrderInfo_whenValidCommandProvided() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 2)), null);
            OrderModel builtOrder = new OrderModel(USER_ID);

            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of(product));
            given(stockRepository.decreaseStock(PRODUCT_ID, 2)).willReturn(1);
            given(orderDomainService.buildOrder(eq(USER_ID), any(), any())).willReturn(builtOrder);
            given(orderRepository.save(builtOrder)).willReturn(builtOrder);

            // act
            OrderInfo result = orderFacade.createOrder(command);

            // assert
            then(stockRepository).should().decreaseStock(PRODUCT_ID, 2);
            then(orderDomainService).should().buildOrder(eq(USER_ID), any(), any());
            assertAll(
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("존재하지 않는 상품에 주문 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(999L, 1)), null);
            given(productRepository.findAllActiveByIds(List.of(999L))).willReturn(List.of());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(orderRepository).should(never()).save(any());
        }

        @DisplayName("재고가 부족한 경우 (affected = 0) BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 5)), null);
            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of(product));
            given(stockRepository.decreaseStock(eq(PRODUCT_ID), anyInt())).willReturn(0);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(orderRepository).should(never()).save(any());
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(), null);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(productRepository).should(never()).findAllActiveByIds(any());
        }

        @DisplayName("삭제된 상품에 주문 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange — findAllActiveByIds는 삭제된 상품을 반환하지 않음
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 1)), null);
            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("중복된 productId가 포함된 주문 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProductIdProvided() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID,
                List.of(new OrderItemCommand(PRODUCT_ID, 1), new OrderItemCommand(PRODUCT_ID, 2)), null);
            given(productRepository.findAllActiveByIds(any())).willReturn(List.of(product));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(stockRepository).should(never()).decreaseStock(any(), anyInt());
        }

        @DisplayName("수량이 0인 주문 항목이 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID,
                List.of(new OrderItemCommand(PRODUCT_ID, 0)), null);
            given(productRepository.findAllActiveByIds(any())).willReturn(List.of(product));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(stockRepository).should(never()).decreaseStock(any(), anyInt());
        }
    }

    @DisplayName("getOrder()를 호출할 때,")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 조회 시 OrderInfo가 반환된다.")
        @Test
        void returnsOrderInfo_whenOrderBelongsToUser() {
            // arrange
            OrderModel order = new OrderModel(USER_ID);
            ReflectionTestUtils.setField(order, "id", 1L);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // act
            OrderInfo result = orderFacade.getOrder(USER_ID, 1L);

            // assert
            assertThat(result.userId()).isEqualTo(USER_ID);
        }

        @DisplayName("타인의 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderBelongsToOtherUser() {
            // arrange
            OrderModel order = new OrderModel(99L);
            ReflectionTestUtils.setField(order, "id", 1L);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.getOrder(USER_ID, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.getOrder(USER_ID, 999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
