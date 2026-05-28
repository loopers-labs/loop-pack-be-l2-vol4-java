package com.loopers.order.domain;

import com.loopers.brand.domain.BrandModel;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fake.IdFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreationServiceTest {

    private final OrderCreationService service = new OrderCreationService();

    private ProductModel product(long id, long price, int stock) {
        return IdFixtures.assignId(new ProductModel(1L, "상품" + id, "설명", price, stock), id);
    }

    private final BrandModel brand = IdFixtures.assignId(new BrandModel("브랜드", "설명"), 1L);

    @DisplayName("정상 주문 생성 시,")
    @Nested
    class Success {
        @DisplayName("여러 상품의 라인 금액 합으로 총액이 계산되고 재고가 차감된다.")
        @Test
        void createsOrder_andDeductsStock() {
            ProductModel p1 = product(1L, 1_000L, 10);
            ProductModel p2 = product(2L, 2_000L, 5);
            List<OrderLine> lines = List.of(new OrderLine(1L, 2), new OrderLine(2L, 3));

            OrderModel order =
                service.create(100L, lines, Map.of(1L, p1, 2L, p2), Map.of(1L, brand));

            assertThat(order.getMemberId()).isEqualTo(100L);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(2 * 1_000L + 3 * 2_000L);
            assertThat(p1.getStock()).isEqualTo(8);
            assertThat(p2.getStock()).isEqualTo(2);
        }

        @DisplayName("주문 항목에 주문 당시 상품 정보가 스냅샷으로 보존된다.")
        @Test
        void preservesSnapshot() {
            ProductModel p1 = product(1L, 1_000L, 10);
            OrderModel order =
                service.create(100L, List.of(new OrderLine(1L, 1)), Map.of(1L, p1), Map.of(1L, brand));

            OrderItem item = order.getItems().get(0);
            assertThat(item.getSnapshot().getProductName()).isEqualTo("상품1");
            assertThat(item.getSnapshot().getBrandName()).isEqualTo("브랜드");
            assertThat(item.getSnapshot().getUnitPrice()).isEqualTo(1_000L);
        }
    }

    @DisplayName("예외 주문 흐름에서,")
    @Nested
    class Failure {
        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenStockInsufficient() {
            ProductModel p1 = product(1L, 1_000L, 1);
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () ->
                        service.create(
                            100L, List.of(new OrderLine(1L, 2)), Map.of(1L, p1), Map.of(1L, brand)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLinesEmpty() {
            CoreException result =
                assertThrows(
                    CoreException.class, () -> service.create(100L, List.of(), Map.of(), Map.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () -> service.create(100L, List.of(new OrderLine(999L, 1)), Map.of(), Map.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
