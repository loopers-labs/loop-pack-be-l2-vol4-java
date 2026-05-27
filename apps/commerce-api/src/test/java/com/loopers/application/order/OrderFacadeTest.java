package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @InjectMocks
    private OrderFacade orderFacade;

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;
    @Mock private OrderDomainService orderDomainService;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private ProductModel product;
    private StockModel stock;

    @BeforeEach
    void setUp() {
        BrandModel brand = new BrandModel("Nike", "스포츠 브랜드");
        product = new ProductModel(brand, "나이키 에어맥스", 150_000);
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        stock = new StockModel(product, 100);
    }

    @DisplayName("createOrder()를 호출할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 주문 생성 시 OrderDomainService에 재고 검증과 주문 조립을 위임하고 OrderInfo가 반환된다.")
        @Test
        void returnsOrderInfoAndDecreaseStock_whenValidCommandProvided() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 2)));
            OrderModel builtOrder = new OrderModel(USER_ID); // Domain Service가 조립한 주문 엔티티

            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of(product));
            given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
            given(orderDomainService.buildOrder(eq(USER_ID), any(), any())).willReturn(builtOrder);
            given(orderRepository.save(builtOrder)).willReturn(builtOrder);

            // act
            OrderInfo result = orderFacade.createOrder(command);

            // assert: OrderDomainService 위임 검증
            then(orderDomainService).should().validateStocks(any(), any());
            then(orderDomainService).should().buildOrder(eq(USER_ID), any(), any());

            // assert: 재고 차감 (StockModel.decrease 실제 호출 확인)
            assertThat(stock.getQuantity()).isEqualTo(98); // 100 - 2

            // assert: 결과
            assertAll(
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("존재하지 않는 상품에 주문 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(999L, 1)));
            given(productRepository.findAllActiveByIds(List.of(999L))).willReturn(List.of());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(orderRepository).should(never()).save(any());
        }

        @DisplayName("재고가 부족한 경우 OrderDomainService가 BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 5)));
            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of(product));
            given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(stock));
            willThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                .given(orderDomainService).validateStocks(any(), any()); // Domain Service 위임 확인

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
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of());

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
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of(new OrderItemCommand(PRODUCT_ID, 1)));
            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
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
