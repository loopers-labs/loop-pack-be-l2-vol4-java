package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderService 순수 단위 테스트 — Repository/ProductService/BrandService를 mock으로 격리해
 * DB 없이 주문 생성(placeOrderPending)의 정상/예외 흐름을 검증한다.
 * (실제 영속·재고 롤백은 OrderServiceIntegrationTest가 Testcontainers로 검증)
 */
class OrderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long BRAND_ID = 5L;

    private OrderRepository orderRepository;
    private ProductService productService;
    private BrandService brandService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productService = mock(ProductService.class);
        brandService = mock(BrandService.class);
        orderService = new OrderService(orderRepository, productService, brandService);
    }

    private ProductModel product(Long id, long price, int stock) {
        return ProductModel.reconstitute(id, BRAND_ID, "상품" + id, "설명", null, price, stock, 0L, null);
    }

    private BrandModel brand() {
        return BrandModel.reconstitute(BRAND_ID, "나이키", "스포츠", null);
    }

    @Nested
    @DisplayName("정상 주문")
    class Success {

        @DisplayName("여러 상품을 주문하면 각 재고를 차감하고 totalAmount = Σ lineTotal로 PENDING 주문이 생성된다.")
        @Test
        void given_validLines_when_placeOrderPending_then_deductsAndAggregates() {
            when(productService.getActiveProduct(10L)).thenReturn(product(10L, 10000L, 10));
            when(productService.getActiveProduct(20L)).thenReturn(product(20L, 5000L, 10));
            when(brandService.getActiveBrand(BRAND_ID)).thenReturn(brand());
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderModel order = orderService.placeOrderPending(USER_ID, PaymentMethod.CARD,
                    List.of(new OrderLine(10L, 2), new OrderLine(20L, 1)));

            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.getItems()).hasSize(2),
                    () -> assertThat(order.getTotalAmount().getAmount()).isEqualTo(25000L)   // 10000*2 + 5000*1
            );
            verify(productService).deductStock(10L, 2);
            verify(productService).deductStock(20L, 1);
            verify(orderRepository).save(any(OrderModel.class));
        }
    }

    @Nested
    @DisplayName("예외 주문")
    class Failure {

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST가 발생하고, 재고 차감·저장을 하지 않는다.")
        @Test
        void given_emptyLines_when_placeOrderPending_then_badRequest() {
            CoreException ex = assertThrows(CoreException.class,
                    () -> orderService.placeOrderPending(USER_ID, PaymentMethod.CARD, List.of()));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verifyNoInteractions(productService);
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("존재하지 않는 상품이 포함되면 NOT_FOUND가 발생하고, 재고 차감·저장을 하지 않는다.")
        @Test
        void given_nonExistingProduct_when_placeOrderPending_then_notFound() {
            when(productService.getActiveProduct(9999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            CoreException ex = assertThrows(CoreException.class,
                    () -> orderService.placeOrderPending(USER_ID, PaymentMethod.CARD, List.of(new OrderLine(9999L, 1))));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(productService, never()).deductStock(anyLong(), anyInt());
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("재고가 부족하면 CONFLICT가 발생하고, 주문을 저장하지 않는다.")
        @Test
        void given_insufficientStock_when_placeOrderPending_then_conflict() {
            when(productService.getActiveProduct(10L)).thenReturn(product(10L, 10000L, 1));
            when(brandService.getActiveBrand(BRAND_ID)).thenReturn(brand());
            doThrow(new CoreException(ErrorType.CONFLICT, "재고가 부족합니다."))
                    .when(productService).deductStock(10L, 5);

            CoreException ex = assertThrows(CoreException.class,
                    () -> orderService.placeOrderPending(USER_ID, PaymentMethod.CARD, List.of(new OrderLine(10L, 5))));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(orderRepository, never()).save(any());
        }
    }
}
