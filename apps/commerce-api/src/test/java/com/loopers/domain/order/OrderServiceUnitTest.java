package com.loopers.domain.order;

import com.loopers.domain.product.ProductDescription;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductPrice;
import com.loopers.domain.product.ProductService;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @Mock
    private ProductStockService productStockService;

    @InjectMocks
    private OrderService orderService;

    private ProductModel sampleProduct(String name, Long price) {
        return ProductModel.of(
                1L,
                ProductName.of(name),
                ProductDescription.of(name + " 설명"),
                ProductPrice.of(price)
        );
    }

    @DisplayName("주문 PENDING 생성할 때")
    @Nested
    class CreatePendingOrder {

        @DisplayName("각 상품 정보를 조회해 OrderItem 스냅샷을 만들고, 재고 차감 후 저장한다.")
        @Test
        void createsOrder_andDecreasesStock() {
            // given
            ProductModel p1 = sampleProduct("A", 10000L);
            ProductModel p2 = sampleProduct("B", 20000L);
            given(productService.getProduct(1L)).willReturn(p1);
            given(productService.getProduct(2L)).willReturn(p2);
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // when
            OrderModel result = orderService.createPendingOrder(99L, List.of(
                    OrderLine.of(1L, 2),
                    OrderLine.of(2L, 3)
            ));

            // then
            verify(productStockService).decreaseStock(1L, 2);
            verify(productStockService).decreaseStock(2L, 3);
            verify(orderRepository).save(any(OrderModel.class));
            assertAll(
                    () -> assertThat(result.getUserId()).isEqualTo(99L),
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(result.getTotalAmount()).isEqualTo(10000L * 2 + 20000L * 3),
                    () -> assertThat(result.getOrderItems()).hasSize(2)
            );
        }
    }

    @DisplayName("주문 확정할 때")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태가 PAID로 변경된다.")
        @Test
        void transitionsToPaid() {
            // given
            OrderModel pending = OrderModel.of(99L, List.of(
                    OrderItemModel.of(1L, "A", 10000L, 2)
            ));
            given(orderRepository.find(1L)).willReturn(Optional.of(pending));

            // when
            OrderModel result = orderService.confirm(1L);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("존재하지 않는 주문이면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMissing() {
            // given
            given(orderRepository.find(1L)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderService.confirm(1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 실패 처리할 때")
    @Nested
    class Fail {

        @DisplayName("각 항목 수량만큼 재고가 복구되고, 주문은 PENDING 상태가 FAILED로 변경된다.")
        @Test
        void restoresStock_andMarksFailed() {
            // given
            OrderModel pending = OrderModel.of(99L, List.of(
                    OrderItemModel.of(1L, "A", 10000L, 2),
                    OrderItemModel.of(2L, "B", 20000L, 3)
            ));
            given(orderRepository.find(1L)).willReturn(Optional.of(pending));

            // when
            OrderModel result = orderService.fail(1L);

            // then
            verify(productStockService).increaseStock(1L, 2);
            verify(productStockService).increaseStock(2L, 3);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        }
    }
}