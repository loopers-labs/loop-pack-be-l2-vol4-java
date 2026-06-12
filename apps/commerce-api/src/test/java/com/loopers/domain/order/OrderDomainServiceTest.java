package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class OrderDomainServiceTest {

    private final OrderDomainService orderDomainService = new OrderDomainService();

    private ProductModel product(Long id, String name, Long price, Integer stock) {
        return new ProductModel(id, 1L, name, "설명", price, stock, null, null);
    }

    @DisplayName("주문을 생성하면, 재고를 차감하고 상품 정보를 스냅샷으로 담은 주문을 반환한다.")
    @Test
    void deductsStock_andSnapshotsProductInfo() {
        // arrange
        ProductModel productA = product(1L, "상품A", 1_000L, 10);
        ProductModel productB = product(2L, "상품B", 2_000L, 5);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(1L, 2);
        quantities.put(2L, 3);
        Map<Long, ProductModel> products = Map.of(1L, productA, 2L, productB);

        // act
        OrderModel order = orderDomainService.place(100L, quantities, products, 0L);

        // assert
        assertAll(
            () -> assertThat(order.getUserId()).isEqualTo(100L),
            () -> assertThat(order.getTotalPrice()).isEqualTo(2 * 1_000L + 3 * 2_000L),
            () -> assertThat(order.getOrderLines()).hasSize(2),
            () -> assertThat(productA.getStock()).isEqualTo(8),
            () -> assertThat(productB.getStock()).isEqualTo(2),
            () -> assertThat(order.getOrderLines().get(0).getProductName()).isEqualTo("상품A")
        );
    }

    @DisplayName("주문 상품이 products 맵에 없으면 NOT_FOUND 예외가 발생한다.")
    @Test
    void throwsNotFound_whenProductIsMissing() {
        // arrange
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(99L, 1);
        Map<Long, ProductModel> products = Map.of();

        // act
        CoreException result = assertThrows(CoreException.class,
            () -> orderDomainService.place(100L, quantities, products, 0L));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenStockIsInsufficient() {
        // arrange
        ProductModel productA = product(1L, "상품A", 1_000L, 1);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(1L, 2);
        Map<Long, ProductModel> products = Map.of(1L, productA);

        // act
        CoreException result = assertThrows(CoreException.class,
            () -> orderDomainService.place(100L, quantities, products, 0L));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
