package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private UserService userService;

    @Mock
    private ProductService productService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderFacade orderFacade;

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 재고를 차감하고 주문을 생성한다.")
        @Test
        void createsOrder_andDeductsStock_whenRequestIsValid() {
            // arrange
            UserModel user = new UserModel("user1", "pw1");
            ProductModel product = new ProductModel(1L, "에어맥스 90", "편한 신발", 159000L, 10);
            OrderModel order = new OrderModel(user.getId(), 318000L, 0L, 318000L, null);
            OrderItemModel item = new OrderItemModel(order.getId(), product.getId(), "에어맥스 90", 159000L, 2);

            when(userService.getUser("user1", "pw1")).thenReturn(user);
            when(productService.getProduct(anyLong())).thenReturn(product);
            when(productService.deductStock(anyLong(), anyInt())).thenReturn(product);
            when(orderService.createOrder(anyLong(), anyLong(), anyLong(), anyLong(), nullable(Long.class), any())).thenReturn(order);
            when(orderService.getOrderItems(anyLong())).thenReturn(List.of(item));

            // act
            OrderInfo result = orderFacade.createOrder("user1", "pw1",
                List.of(new OrderFacade.OrderRequest(1L, 2)), null);

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(result.originalPrice()).isEqualTo(318000L),
                () -> assertThat(result.items()).hasSize(1)
            );
            verify(productService).deductStock(anyLong(), eq(2));
        }

        @DisplayName("유저가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // arrange
            when(userService.getUser("unknown", "pw"))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("unknown", "pw",
                    List.of(new OrderFacade.OrderRequest(1L, 1)), null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(productService, never()).deductStock(anyLong(), anyInt());
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            UserModel user = new UserModel("user1", "pw1");
            when(userService.getUser("user1", "pw1")).thenReturn(user);
            when(productService.getProduct(anyLong()))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(999L, 1)), null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderService, never()).createOrder(anyLong(), anyLong(), anyLong(), anyLong(), nullable(Long.class), any());
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_andDoesNotCreateOrder_whenStockIsInsufficient() {
            // arrange
            UserModel user = new UserModel("user1", "pw1");
            ProductModel product = new ProductModel(1L, "에어맥스 90", "편한 신발", 159000L, 1);
            when(userService.getUser("user1", "pw1")).thenReturn(user);
            when(productService.getProduct(anyLong())).thenReturn(product);
            when(productService.deductStock(anyLong(), anyInt()))
                .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(1L, 5)), null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderService, never()).createOrder(anyLong(), anyLong(), anyLong(), anyLong(), nullable(Long.class), any());
        }
    }
}
