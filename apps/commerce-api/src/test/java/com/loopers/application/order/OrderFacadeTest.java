package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.ProductSnapshot;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductReader;
import com.loopers.domain.product.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock OrderService orderService;
    @Mock ProductReader productReader;
    @Mock ProductStockService productStockService;
    @Mock CartService cartService;
    @Mock BrandReader brandReader;

    @InjectMocks OrderFacade orderFacade;

    private final List<OrderFacade.OrderRequest> oneItemRequest =
        List.of(new OrderFacade.OrderRequest(10L, 2));

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 흐름: 재고 차감 + 장바구니 삭제 + 주문 생성이 순서대로 호출된다.")
        @Test
        void creates_inCorrectOrder() {
            ProductModel product = new ProductModel(1L, "나이키 신발", "설명", 50000L);
            ReflectionTestUtils.setField(product, "id", 10L);

            Brand brand = new Brand("나이키");
            Order order = new Order(1L, List.of(
                new OrderItem(10L, 2, new ProductSnapshot("나이키 신발", 50000L, "나이키"))
            ));

            when(productReader.getProduct(10L)).thenReturn(product);
            when(brandReader.getBrand(1L)).thenReturn(brand);
            when(cartService.getCartItems(1L)).thenReturn(List.of());
            when(orderService.createOrder(anyLong(), any())).thenReturn(order);

            orderFacade.createOrder(1L, oneItemRequest);

            verify(productStockService).decreaseStock(10L, 2);
            verify(orderService).createOrder(eq(1L), any());
        }

        @DisplayName("존재하지 않는 상품이 포함된 경우, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            when(productReader.getProduct(10L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(1L, oneItemRequest));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(productStockService, never()).decreaseStock(anyLong(), anyInt());
            verify(orderService, never()).createOrder(anyLong(), any());
        }

        @DisplayName("재고가 부족한 경우, BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenStockInsufficient() {
            ProductModel product = new ProductModel(1L, "나이키 신발", "설명", 50000L);
            ReflectionTestUtils.setField(product, "id", 10L);

            Brand brand = new Brand("나이키");

            when(productReader.getProduct(10L)).thenReturn(product);
            when(brandReader.getBrand(1L)).thenReturn(brand);
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                .when(productStockService).decreaseStock(10L, 2);

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(1L, oneItemRequest));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderService, never()).createOrder(anyLong(), any());
        }

        @DisplayName("주문 항목이 비어있는 경우, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(1L, List.of()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
