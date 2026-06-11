package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    @DisplayName("주문 항목 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력으로 생성하면 각 필드가 정상 설정된다.")
        @Test
        void createsOrderItemModel_withGivenValues() {
            // given
            Long productId = 1L;
            String productName = "테스트 상품";
            BigDecimal productPrice = BigDecimal.valueOf(10000);
            Long quantity = 2L;

            // when
            OrderItemModel item = new OrderItemModel(productId, productName, productPrice, quantity);

            // then
            assertAll(
                    () -> assertThat(item.getProductId()).isEqualTo(productId),
                    () -> assertThat(item.getProductSnapshot().name()).isEqualTo(productName),
                    () -> assertThat(item.getProductSnapshot().price()).isEqualByComparingTo(productPrice),
                    () -> assertThat(item.getQuantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("productName이 null이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @ParameterizedTest
        void throwsBadRequest_whenProductNameIsNullOrBlank(String productName) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItemModel(1L, productName, BigDecimal.valueOf(10000), 1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productPrice가 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenProductPriceIsNull(BigDecimal productPrice) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItemModel(1L, "테스트 상품", productPrice, 1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productPrice가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -10000L})
        @ParameterizedTest
        void throwsBadRequest_whenProductPriceIsNegative(long productPrice) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItemModel(1L, "테스트 상품", BigDecimal.valueOf(productPrice), 1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItemModel(1L, "테스트 상품", BigDecimal.valueOf(10000), null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 0이하이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {0L, -1L})
        @ParameterizedTest
        void throwsBadRequest_whenQuantityIsZeroOrNegative(long quantity) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItemModel(1L, "테스트 상품", BigDecimal.valueOf(10000), quantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 항목의 소계를 계산할 때,")
    @Nested
    class Subtotal {

        @DisplayName("가격 × 수량으로 소계가 계산된다.")
        @Test
        void returnsCorrectSubtotal() {
            // given
            BigDecimal price = BigDecimal.valueOf(10000);
            Long quantity = 3L;
            OrderItemModel item = new OrderItemModel(1L, "테스트 상품", price, quantity);

            // when
            BigDecimal result = item.subtotal();

            // then
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }
    }
}
