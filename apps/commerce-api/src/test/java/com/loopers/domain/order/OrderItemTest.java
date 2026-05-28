package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("OrderItem 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsOrderItem_whenAllFieldsAreValid() {
            // given
            Long orderId = 1L;
            Long productId = 100L;
            int quantity = 2;
            String productName = "에어맥스 270";
            Long productPrice = 159_000L;
            String brandName = "나이키";

            // when
            OrderItem item = OrderItem.of(orderId, productId, quantity, productName, productPrice, brandName);

            // then
            assertAll(
                () -> assertThat(item.getOrderId()).isEqualTo(orderId),
                () -> assertThat(item.getProductId()).isEqualTo(productId),
                () -> assertThat(item.getQuantity()).isEqualTo(quantity),
                () -> assertThat(item.getProductName()).isEqualTo(productName),
                () -> assertThat(item.getProductPrice()).isEqualTo(productPrice),
                () -> assertThat(item.getBrandName()).isEqualTo(brandName)
            );
        }

        @DisplayName("productPrice 가 0 이어도, 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenProductPriceIsZero() {
            // given
            Long orderId = 1L;
            Long productId = 100L;
            int quantity = 1;
            String productName = "프로모션 사은품";
            Long productPrice = 0L;
            String brandName = "아디다스";

            // when
            OrderItem item = OrderItem.of(orderId, productId, quantity, productName, productPrice, brandName);

            // then
            assertThat(item.getProductPrice()).isEqualTo(0L);
        }

        @DisplayName("orderId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenOrderIdIsNull() {
            // given
            Long orderId = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(orderId, 100L, 1, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("주문 ID 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // given
            Long productId = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, productId, 1, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 ID 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("quantity 가 0 이면, INVALID_QUANTITY 예외가 발생한다.")
        @Test
        void throwsInvalidQuantityException_whenQuantityIsZero() {
            // given
            int quantity = 0;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, quantity, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> assertThat(result.getCustomMessage()).isEqualTo("주문 수량은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("quantity 가 음수이면, INVALID_QUANTITY 예외가 발생한다.")
        @Test
        void throwsInvalidQuantityException_whenQuantityIsNegative() {
            // given
            int quantity = -1;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, quantity, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> assertThat(result.getCustomMessage()).isEqualTo("주문 수량은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("productName 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsNull() {
            // given
            String productName = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, productName, 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productName 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsEmpty() {
            // given
            String productName = "";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, productName, 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productName 이 공백 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsBlank() {
            // given
            String productName = "   ";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, productName, 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productPrice 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductPriceIsNull() {
            // given
            Long productPrice = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, "에어맥스 270", productPrice, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 가격 스냅샷은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("productPrice 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductPriceIsNegative() {
            // given
            Long productPrice = -1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, "에어맥스 270", productPrice, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 가격 스냅샷은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("brandName 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandNameIsNull() {
            // given
            String brandName = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, "에어맥스 270", 159_000L, brandName));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("brandName 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandNameIsEmpty() {
            // given
            String brandName = "";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, "에어맥스 270", 159_000L, brandName));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("brandName 이 공백 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandNameIsBlank() {
            // given
            String brandName = "   ";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderItem.of(1L, 100L, 1, "에어맥스 270", 159_000L, brandName));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명 스냅샷은 비어있을 수 없습니다.")
            );
        }
    }
}
