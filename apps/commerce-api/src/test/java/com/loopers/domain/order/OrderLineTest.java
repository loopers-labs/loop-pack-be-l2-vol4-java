package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class OrderLineTest {

    @DisplayName("주문 항목을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("총액은 단가 * 수량으로 계산된다.")
        @Test
        void calculatesTotalPrice() {
            // act
            OrderLine line = OrderLine.create(1L, "상품A", 1_000L, 3);

            // assert
            assertAll(
                () -> assertThat(line.getProductId()).isEqualTo(1L),
                () -> assertThat(line.getProductName()).isEqualTo("상품A"),
                () -> assertThat(line.getProductPrice()).isEqualTo(1_000L),
                () -> assertThat(line.getQuantity()).isEqualTo(3),
                () -> assertThat(line.getTotalPrice()).isEqualTo(3_000L)
            );
        }

        @DisplayName("수량이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            CoreException result = assertThrows(CoreException.class,
                () -> OrderLine.create(1L, "상품A", 1_000L, 0));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명 스냅샷이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> OrderLine.create(1L, "  ", 1_000L, 1));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
