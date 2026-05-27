package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    @DisplayName("OrderItem 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 각 필드가 올바르게 저장된다.")
        @Test
        void createsOrderItemModel_whenAllFieldsAreValid() {
            // arrange
            Long productId = 1L;
            String productName = "에어맥스";
            Long price = 150000L;
            Integer quantity = 2;

            // act
            OrderItemModel item = new OrderItemModel(productId, productName, price, quantity);

            // assert
            assertAll(
                () -> assertThat(item.getProductId()).isEqualTo(productId),
                () -> assertThat(item.getProductName()).isEqualTo(productName),
                () -> assertThat(item.getPrice()).isEqualTo(price),
                () -> assertThat(item.getQuantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("price가 0이면, 정상 생성된다.")
        @Test
        void createsOrderItemModel_whenPriceIsZero() {
            // act & assert
            assertDoesNotThrow(() -> new OrderItemModel(1L, "에어맥스", 0L, 1));
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(null, "에어맥스", 150000L, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, null, 150000L, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "", 150000L, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "   ", 150000L, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "에어맥스", null, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "에어맥스", -1L, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "에어맥스", 150000L, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "에어맥스", 150000L, 0)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new OrderItemModel(1L, "에어맥스", 150000L, -1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
