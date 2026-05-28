package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderV1ControllerTest {

    private OrderFacade orderFacade;
    private OrderV1Controller orderV1Controller;

    @BeforeEach
    void setUp() {
        orderFacade = mock(OrderFacade.class);
        orderV1Controller = new OrderV1Controller(orderFacade);
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 생성된 orderId를 반환한다.")
        @Test
        void returnsOrderId_whenRequestIsValid() {
            // Arrange
            when(orderFacade.createOrder(eq("user1"), any())).thenReturn(42L);
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.CreateRequest.OrderItemDto(1L, 3))
            );

            // Act
            var result = orderV1Controller.createOrder("user1", request);

            // Assert
            assertThat(result.data().orderId()).isEqualTo(42L);
        }

        @DisplayName("존재하지 않는 회원이면, NOT_FOUND 예외가 전파된다.")
        @Test
        void propagatesNotFound_whenMemberDoesNotExist() {
            // Arrange
            when(orderFacade.createOrder(eq("unknown"), any()))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.CreateRequest.OrderItemDto(1L, 1))
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderV1Controller.createOrder("unknown", request)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고 부족이면, BAD_REQUEST 예외가 전파된다.")
        @Test
        void propagatesBadRequest_whenStockIsInsufficient() {
            // Arrange
            when(orderFacade.createOrder(eq("user1"), any()))
                .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족한 상품이 있습니다."));
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.CreateRequest.OrderItemDto(1L, 999))
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderV1Controller.createOrder("user1", request)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
