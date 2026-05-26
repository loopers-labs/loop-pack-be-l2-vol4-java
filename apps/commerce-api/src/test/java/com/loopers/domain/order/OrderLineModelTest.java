package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLineModelTest {

    @DisplayName("주문 상품 라인을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsOrderLineModel_whenAllFieldsAreValid() {
            // act
            OrderLineModel orderLine = new OrderLineModel(1L, "니트", 30_000L, 2);

            // assert
            assertAll(
                () -> assertThat(orderLine.getProductId()).isEqualTo(1L),
                () -> assertThat(orderLine.getProductName()).isEqualTo("니트"),
                () -> assertThat(orderLine.getPrice()).isEqualTo(30_000L),
                () -> assertThat(orderLine.getQuantity()).isEqualTo(2),
                () -> assertThat(orderLine.getAmount()).isEqualTo(60_000L)
            );
        }

        @DisplayName("수량이 1 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsLessThanOne() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderLineModel(1L, "니트", 30_000L, 0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
