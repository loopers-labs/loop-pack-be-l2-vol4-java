package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    private static final Long PRODUCT_ID = 1L;
    private static final String PRODUCT_NAME = "에어맥스 95";
    private static final Money UNIT_PRICE = Money.of(189_000L);
    private static final int QUANTITY = 2;

    @DisplayName("OrderItem 을 of 로 생성할 때, ")
    @Nested
    class Of {

        @DisplayName("정상 값이면 모든 필드가 그대로 보관된다.")
        @Test
        void createsOrderItem_whenValid() {
            // act
            OrderItem item = OrderItem.of(PRODUCT_ID, PRODUCT_NAME, UNIT_PRICE, QUANTITY);

            // assert
            assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getProductName()).isEqualTo(PRODUCT_NAME);
            assertThat(item.getUnitPrice()).isEqualTo(UNIT_PRICE);
            assertThat(item.getQuantity()).isEqualTo(QUANTITY);
        }

        @DisplayName("productId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> OrderItem.of(null, PRODUCT_NAME, UNIT_PRICE, QUANTITY));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName 이 null 이거나 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "\t"})
        void throwsBadRequest_whenProductNameIsBlank(String invalid) {
            CoreException result = assertThrows(CoreException.class,
                    () -> OrderItem.of(PRODUCT_ID, invalid, UNIT_PRICE, QUANTITY));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("unitPrice 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUnitPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> OrderItem.of(PRODUCT_ID, PRODUCT_NAME, null, QUANTITY));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity 가 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        void throwsBadRequest_whenQuantityBelowOne(int invalid) {
            CoreException result = assertThrows(CoreException.class,
                    () -> OrderItem.of(PRODUCT_ID, PRODUCT_NAME, UNIT_PRICE, invalid));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("OrderItem 의 subtotal 은 ")
    @Nested
    class Subtotal {

        @DisplayName("unitPrice × quantity 로 계산된다.")
        @Test
        void returnsUnitPriceTimesQuantity() {
            // arrange
            OrderItem item = OrderItem.of(PRODUCT_ID, PRODUCT_NAME, Money.of(100L), 3);

            // act
            Money subtotal = item.subtotal();

            // assert
            assertThat(subtotal.getAmount()).isEqualTo(300L);
        }

        @DisplayName("수량이 1 일 때 단가와 동일하다.")
        @Test
        void equalsUnitPrice_whenQuantityOne() {
            OrderItem item = OrderItem.of(PRODUCT_ID, PRODUCT_NAME, Money.of(99_000L), 1);
            assertThat(item.subtotal().getAmount()).isEqualTo(99_000L);
        }
    }
}
