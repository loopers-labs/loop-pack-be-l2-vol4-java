package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrderModelTest {

    private OrderItem item(long productId, long unitPrice, int quantity) {
        return new OrderItem(productId, "에어맥스", "나이키", "http://img/a.png", new Money(unitPrice), quantity);
    }

    @Nested
    @DisplayName("OrderItem 생성")
    class CreateOrderItem {

        @DisplayName("생성 시 lineTotal = unitPrice × quantity가 계산되고 스냅샷이 보존된다")
        @Test
        void given_validInput_when_create_then_lineTotalComputed() {
            OrderItem item = new OrderItem(1L, "에어맥스", "나이키", "http://img/a.png", new Money(10000L), 3);

            assertAll(
                    () -> assertThat(item.getProductId()).isEqualTo(1L),
                    () -> assertThat(item.getProductName()).isEqualTo("에어맥스"),
                    () -> assertThat(item.getBrandName()).isEqualTo("나이키"),
                    () -> assertThat(item.getUnitPrice()).isEqualTo(new Money(10000L)),
                    () -> assertThat(item.getQuantity()).isEqualTo(3),
                    () -> assertThat(item.getLineTotal()).isEqualTo(new Money(30000L))
            );
        }

        @DisplayName("수량이 1 미만이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositiveQuantity_when_create_then_throwsBadRequest(int quantity) {
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItem(1L, "에어맥스", "나이키", null, new Money(10000L), quantity));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("OrderModel 생성과 집계")
    class CreateAndTotals {

        @DisplayName("생성하면 PENDING 상태이고 항목이 비어있다")
        @Test
        void given_validInput_when_create_then_pending() {
            OrderModel order = new OrderModel(100L, PaymentMethod.CARD);

            assertAll(
                    () -> assertThat(order.getUserId()).isEqualTo(100L),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CARD),
                    () -> assertThat(order.getItems()).isEmpty()
            );
        }

        @DisplayName("생성 즉시 TSID 식별자가 부여된다(저장 전 non-null, 매 생성 고유)")
        @Test
        void given_create_when_constructed_then_assignsUniqueTsid() {
            OrderModel first = new OrderModel(100L, PaymentMethod.CARD);
            OrderModel second = new OrderModel(100L, PaymentMethod.CARD);

            assertAll(
                    () -> assertThat(first.getId()).isNotNull(),
                    () -> assertThat(second.getId()).isNotNull(),
                    () -> assertThat(first.getId()).isNotEqualTo(second.getId()),
                    // TSID는 시간 정렬(단조 증가) — 나중에 만든 주문의 id가 더 크다
                    () -> assertThat(second.getId()).isGreaterThan(first.getId())
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullUserId_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(null, PaymentMethod.CARD));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("항목 추가 후 calculateTotals하면 totalAmount = Σ lineTotal이다")
        @Test
        void given_items_when_calculateTotals_then_sumOfLineTotals() {
            OrderModel order = new OrderModel(100L, PaymentMethod.CARD);
            order.addItem(item(1L, 10000L, 2));   // 20000
            order.addItem(item(2L, 5000L, 3));    // 15000

            order.calculateTotals();

            assertAll(
                    () -> assertThat(order.getItems()).hasSize(2),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(new Money(35000L))
            );
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StateMachine {

        @DisplayName("PENDING에서 markPaid하면 PAID가 되고 paidAt이 기록된다")
        @Test
        void given_pending_when_markPaid_then_paid() {
            OrderModel order = new OrderModel(100L, PaymentMethod.CARD);

            order.markPaid();

            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
                    () -> assertThat(order.getPaidAt()).isNotNull()
            );
        }

        @DisplayName("PENDING에서 markFailed하면 FAILED가 되고 사유가 기록된다")
        @Test
        void given_pending_when_markFailed_then_failed() {
            OrderModel order = new OrderModel(100L, PaymentMethod.CARD);

            order.markFailed("카드 한도 초과");

            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                    () -> assertThat(order.getFailureReason()).isEqualTo("카드 한도 초과")
            );
        }

        @DisplayName("이미 PAID인 주문을 다시 전이하려 하면 CONFLICT 예외가 발생한다")
        @Test
        void given_paid_when_markFailed_then_throwsConflict() {
            OrderModel order = new OrderModel(100L, PaymentMethod.CARD);
            order.markPaid();

            CoreException result = assertThrows(CoreException.class, () -> order.markFailed("이유"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("이미 FAILED인 주문을 markPaid하려 하면 CONFLICT 예외가 발생한다")
        @Test
        void given_failed_when_markPaid_then_throwsConflict() {
            OrderModel order = new OrderModel(100L, PaymentMethod.CARD);
            order.markFailed("이유");

            CoreException result = assertThrows(CoreException.class, order::markPaid);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
