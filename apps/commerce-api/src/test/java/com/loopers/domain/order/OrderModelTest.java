package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("userId 로 생성하면 PENDING 상태로 생성된다.")
        @Test
        void createsOrder_whenUserIdIsValid() {
            // act
            OrderModel order = new OrderModel(1L);

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getOrderedAt()).isNotNull(),
                () -> assertThat(order.getItems()).isEmpty()
            );
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new OrderModel(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 항목을 추가하면,")
    @Nested
    class AddItem {

        @DisplayName("items 리스트에 추가된다.")
        @Test
        void addsItem() {
            // arrange
            OrderModel order = new OrderModel(1L);
            OrderItemModel item = new OrderItemModel(order, 100L, "신발", 50_000L, 2);

            // act
            order.addItem(item);

            // assert
            assertThat(order.getItems()).containsExactly(item);
        }
    }

    @DisplayName("총액을 계산할 때,")
    @Nested
    class CalculateTotal {

        @DisplayName("항목 소계들의 합을 반환한다 (Money VO 활용).")
        @Test
        void sumsAllSubtotals() {
            // arrange
            OrderModel order = new OrderModel(1L);
            order.addItem(new OrderItemModel(order, 100L, "신발", 50_000L, 2));   // 100,000
            order.addItem(new OrderItemModel(order, 101L, "가방", 30_000L, 1));   // 30,000

            // act
            Long total = order.calculateTotalPrice();

            // assert
            assertThat(total).isEqualTo(130_000L);
        }

        @DisplayName("항목이 없으면 0원이다.")
        @Test
        void returnsZero_whenNoItems() {
            OrderModel order = new OrderModel(1L);
            assertThat(order.calculateTotalPrice()).isZero();
        }

        @DisplayName("confirmAmounts 후 원금/최종금액이 채워지고 할인은 0이다.")
        @Test
        void confirmsAmounts() {
            OrderModel order = new OrderModel(1L);
            order.addItem(new OrderItemModel(order, 100L, "신발", 50_000L, 2));

            order.confirmAmounts();

            assertAll(
                () -> assertThat(order.getOriginalAmount()).isEqualTo(100_000L),
                () -> assertThat(order.getDiscountAmount()).isZero(),
                () -> assertThat(order.getTotalPrice()).isEqualTo(100_000L)
            );
        }
    }

    @DisplayName("쿠폰 할인을 적용할 때,")
    @Nested
    class ApplyDiscount {

        @DisplayName("원금에서 할인액을 빼 최종 결제 금액을 확정한다.")
        @Test
        void appliesDiscount() {
            OrderModel order = new OrderModel(1L);
            order.addItem(new OrderItemModel(order, 100L, "신발", 50_000L, 2));   // 100,000
            order.confirmAmounts();

            order.applyDiscount(com.loopers.domain.common.Money.of(10_000L));

            assertAll(
                () -> assertThat(order.getOriginalAmount()).isEqualTo(100_000L),
                () -> assertThat(order.getDiscountAmount()).isEqualTo(10_000L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(90_000L)
            );
        }

        @DisplayName("할인액이 원금을 초과하면 원금까지만 할인되어 최종 금액은 0이다 (음수 방지).")
        @Test
        void capsDiscountAtOriginal() {
            OrderModel order = new OrderModel(1L);
            order.addItem(new OrderItemModel(order, 100L, "신발", 5_000L, 1));   // 5,000
            order.confirmAmounts();

            order.applyDiscount(com.loopers.domain.common.Money.of(10_000L));

            assertAll(
                () -> assertThat(order.getDiscountAmount()).isEqualTo(5_000L),
                () -> assertThat(order.getTotalPrice()).isZero()
            );
        }
    }

    @DisplayName("주문 상태를 전이할 때,")
    @Nested
    class StateTransition {

        @DisplayName("PENDING → COMPLETED 로 전이된다.")
        @Test
        void completesFromPending() {
            OrderModel order = new OrderModel(1L);
            order.complete();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @DisplayName("PENDING → CANCELLED 로 전이된다.")
        @Test
        void cancelsFromPending() {
            OrderModel order = new OrderModel(1L);
            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 COMPLETED 인 주문을 다시 완료 처리하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCompletingNonPending() {
            OrderModel order = new OrderModel(1L);
            order.complete();

            CoreException result = assertThrows(CoreException.class, order::complete);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 CANCELLED 인 주문을 다시 취소 처리하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCancellingNonPending() {
            OrderModel order = new OrderModel(1L);
            order.cancel();

            CoreException result = assertThrows(CoreException.class, order::cancel);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
