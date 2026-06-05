package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDomainServiceTest {

    private final OrderDomainService orderDomainService = new OrderDomainService();

    // 도메인 서비스는 Product.getId() 로 라인을 매칭하므로, 영속 없이도 식별자가 필요하다.
    // 통합/영속 컨텍스트가 아닌 순수 단위 테스트라 id 를 reflection 으로 주입한다.
    private static Product product(long id, long price, int stock) {
        Product product = Product.create(1L, "상품" + id, Money.of(price), Stock.of(stock));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @DisplayName("OrderDomainService.create 가 ")
    @Nested
    class Create {

        @DisplayName("동일 productId 가 라인에 중복되면 수량을 합산해 단일 OrderItem 으로 만든다.")
        @Test
        void mergesDuplicateProductLines() {
            // arrange
            Product a = product(101L, 1_000L, 10);
            Product b = product(102L, 2_000L, 10);
            List<OrderCommand.OrderLine> lines = List.of(
                    OrderCommand.OrderLine.of(101L, 2),
                    OrderCommand.OrderLine.of(102L, 1),
                    OrderCommand.OrderLine.of(101L, 3)
            );

            // act
            Order order = orderDomainService.create(10L, List.of(a, b), lines);

            // assert — 합산된 항목 2개 (101→5, 102→1), 총액 = 1000*5 + 2000*1 = 7000
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getItems())
                    .extracting(OrderItem::getProductId, OrderItem::getQuantity)
                    .containsExactlyInAnyOrder(Tuple.tuple(101L, 5), Tuple.tuple(102L, 1));
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(7_000L);
        }

        @DisplayName("OrderItem 은 주문 시점의 productName / unitPrice 스냅샷을 보관한다.")
        @Test
        void snapshotsProductNameAndUnitPrice() {
            // arrange
            Product a = product(101L, 1_500L, 10);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 2));

            // act
            Order order = orderDomainService.create(10L, List.of(a), lines);

            // assert
            OrderItem item = order.getItems().get(0);
            assertThat(item.getProductName()).isEqualTo("상품101");
            assertThat(item.getUnitPrice().getAmount()).isEqualTo(1_500L);
        }

        @DisplayName("주문이 통과되면 입력 Product 의 재고가 차감된다.")
        @Test
        void decreasesStockOnInputProducts() {
            // arrange
            Product a = product(101L, 1_000L, 10);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 3));

            // act
            orderDomainService.create(10L, List.of(a), lines);

            // assert
            assertThat(a.getStock().getQuantity()).isEqualTo(7);
        }

        @DisplayName("입력 products 에 없는 productId 가 라인에 있으면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenProductMissing() {
            Product a = product(101L, 1_000L, 10);
            List<OrderCommand.OrderLine> lines = List.of(
                    OrderCommand.OrderLine.of(101L, 1),
                    OrderCommand.OrderLine.of(999L, 1)
            );

            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(a), lines));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족한 상품이 하나라도 있으면 BAD_REQUEST 이고 재고는 변동되지 않는다 (AC-07-3, AC-07-4).")
        @Test
        void throwsBadRequest_whenStockShortage_andRollsBackStock() {
            // arrange — 두 상품 모두 라인 수량이 재고 초과
            Product a = product(101L, 1_000L, 1);  // 요청 5 > 재고 1
            Product b = product(102L, 2_000L, 10); // 요청 2 ≤ 재고 10
            List<OrderCommand.OrderLine> lines = List.of(
                    OrderCommand.OrderLine.of(101L, 5),
                    OrderCommand.OrderLine.of(102L, 2)
            );

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(a, b), lines));

            // assert — 부족 시 재고 변경은 어느 상품도 일어나지 않아야 (검증 후 차감 순서)
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(a.getStock().getQuantity()).isEqualTo(1);
            assertThat(b.getStock().getQuantity()).isEqualTo(10);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST.")
        @Test
        void throwsBadRequest_whenUserIdNull() {
            Product a = product(101L, 1_000L, 10);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 1));
            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(null, List.of(a), lines));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("lines 가 비어있으면 BAD_REQUEST.")
        @Test
        void throwsBadRequest_whenLinesEmpty() {
            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(), List.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
