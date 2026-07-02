package com.loopers.order.domain;

import com.loopers.brand.domain.Brand;
import com.loopers.product.domain.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.IdFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreationServiceTest {

    private final OrderCreationService service = new OrderCreationService();

    private Product product(long id, long price) {
        return IdFixtures.assignId(new Product(1L, "상품" + id, "설명", price), id);
    }

    private final Brand brand = IdFixtures.assignId(new Brand("브랜드", "설명"), 1L);

    @DisplayName("정상 주문 생성 시,")
    @Nested
    class Success {
        @DisplayName("여러 상품의 라인 금액 합으로 총액이 계산된다. (재고 차감은 OrderFacade 책임)")
        @Test
        void createsOrder_andCalculatesTotal() {
            Product p1 = product(1L, 1_000L);
            Product p2 = product(2L, 2_000L);
            List<OrderLine> lines = List.of(new OrderLine(1L, 2), new OrderLine(2L, 3));

            Order order =
                service.create(100L, lines, Map.of(1L, p1, 2L, p2), Map.of(1L, brand));

            assertThat(order.getMemberId()).isEqualTo(100L);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(2 * 1_000L + 3 * 2_000L);
        }

        @DisplayName("주문 항목에 주문 당시 상품 정보가 스냅샷으로 보존된다.")
        @Test
        void preservesSnapshot() {
            Product p1 = product(1L, 1_000L);
            Order order =
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
