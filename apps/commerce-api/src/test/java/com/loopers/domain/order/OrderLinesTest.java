package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLinesTest {

    @DisplayName("OrderLines.from 은 ")
    @Nested
    class From {

        @DisplayName("동일 productId 라인의 수량을 합산한다.")
        @Test
        void mergesDuplicateProductLines() {
            OrderLines lines = OrderLines.from(List.of(
                    OrderCommand.OrderLine.of(101L, 2),
                    OrderCommand.OrderLine.of(102L, 1),
                    OrderCommand.OrderLine.of(101L, 3)
            ));

            assertThat(lines.productIds()).containsExactlyInAnyOrder(101L, 102L);
            assertThat(lines.quantityOf(101L)).isEqualTo(5);
            assertThat(lines.quantityOf(102L)).isEqualTo(1);
        }

        @DisplayName("입력이 null 이면 BAD_REQUEST.")
        @Test
        void throwsBadRequest_whenNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> OrderLines.from(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("입력이 비어있으면 BAD_REQUEST.")
        @Test
        void throwsBadRequest_whenEmpty() {
            CoreException result = assertThrows(CoreException.class,
                    () -> OrderLines.from(List.of()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최초 등장 순서를 유지한다.")
        @Test
        void preservesFirstAppearanceOrder() {
            OrderLines lines = OrderLines.from(List.of(
                    OrderCommand.OrderLine.of(102L, 1),
                    OrderCommand.OrderLine.of(101L, 2),
                    OrderCommand.OrderLine.of(102L, 3)
            ));

            assertThat(lines.productIds()).containsExactly(102L, 101L);
        }
    }

    @DisplayName("OrderLines.quantityOf 는 ")
    @Nested
    class QuantityOf {

        @DisplayName("등록된 productId 의 합산 수량을 돌려준다.")
        @Test
        void returnsMergedQuantity() {
            OrderLines lines = OrderLines.from(List.of(
                    OrderCommand.OrderLine.of(101L, 4)
            ));
            assertThat(lines.quantityOf(101L)).isEqualTo(4);
        }

        @DisplayName("등록되지 않은 productId 면 예외를 던진다.")
        @Test
        void throwsInternalError_whenProductIdNotPresent() {
            OrderLines lines = OrderLines.from(List.of(
                    OrderCommand.OrderLine.of(101L, 1)
            ));
            assertThrows(CoreException.class, () -> lines.quantityOf(999L));
        }
    }
}
