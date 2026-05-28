package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OrderItemEntityTest {

    private static final Long VALID_PRODUCT_ID = 1L;
    private static final String VALID_PRODUCT_NAME = "에어맥스 90";
    private static final Long VALID_PRODUCT_PRICE = 150000L;
    private static final Integer VALID_QUANTITY = 2;

    @DisplayName("주문 항목 생성")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면 성공한다.")
        @Test
        void createsOrderItemEntity_whenRequestIsValid() {
            // act
            OrderItemEntity item = new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, VALID_PRODUCT_PRICE, VALID_QUANTITY);

            // assert
            assertAll(
                    () -> assertEquals(VALID_PRODUCT_ID, item.getProductId()),
                    () -> assertEquals(VALID_PRODUCT_NAME, item.getProductName()),
                    () -> assertEquals(VALID_PRODUCT_PRICE, item.getProductPrice()),
                    () -> assertEquals(VALID_QUANTITY, item.getQuantity())
            );
        }

        @DisplayName("productId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            // act & assert
            assertThrows(CoreException.class, () ->
                    new OrderItemEntity(null, VALID_PRODUCT_NAME, VALID_PRODUCT_PRICE, VALID_QUANTITY));
        }

        @DisplayName("productName이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductNameIsNull() {
            // act & assert
            assertThrows(CoreException.class, () ->
                    new OrderItemEntity(VALID_PRODUCT_ID, null, VALID_PRODUCT_PRICE, VALID_QUANTITY));
        }

        @DisplayName("productName이 빈 문자열이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductNameIsBlank() {
            // act & assert
            assertThrows(CoreException.class, () ->
                    new OrderItemEntity(VALID_PRODUCT_ID, "   ", VALID_PRODUCT_PRICE, VALID_QUANTITY));
        }

        @DisplayName("productPrice가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductPriceIsNull() {
            // act & assert
            assertThrows(CoreException.class, () ->
                    new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, null, VALID_QUANTITY));
        }

        @DisplayName("productPrice가 -1이면 예외가 발생한다. (BVA)")
        @Test
        void throwsException_whenProductPriceIsMinusOne() {
            // act & assert
            assertThrows(CoreException.class, () ->
                    new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, -1L, VALID_QUANTITY));
        }

        @DisplayName("productPrice가 0이면 정상 생성된다. (스냅샷 0원 허용, BVA)")
        @Test
        void createsOrderItemEntity_whenProductPriceIsZero() {
            // act
            OrderItemEntity item = new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, 0L, VALID_QUANTITY);

            // assert
            assertEquals(0L, item.getProductPrice());
        }

        @DisplayName("quantity가 0이면 예외가 발생한다. (BVA)")
        @Test
        void throwsException_whenQuantityIsZero() {
            // act & assert
            assertThrows(CoreException.class, () ->
                    new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, VALID_PRODUCT_PRICE, 0));
        }

        @DisplayName("quantity가 1이면 정상 생성된다. (BVA)")
        @Test
        void createsOrderItemEntity_whenQuantityIsOne() {
            // act
            OrderItemEntity item = new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, VALID_PRODUCT_PRICE, 1);

            // assert
            assertEquals(1, item.getQuantity());
        }
    }

    @DisplayName("주문 항목 금액 계산")
    @Nested
    class Subtotal {

        @DisplayName("subtotal()은 productPrice * quantity를 반환한다.")
        @Test
        void returnsProductPriceMultipliedByQuantity() {
            // arrange
            OrderItemEntity item = new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, VALID_PRODUCT_PRICE, VALID_QUANTITY);

            // act & assert
            assertEquals(VALID_PRODUCT_PRICE * VALID_QUANTITY, item.subtotal());
        }

        @DisplayName("quantity가 3이고 price가 1000이면 subtotal은 3000이다.")
        @Test
        void returnsCorrectSubtotal_whenQuantityIsThreeAndPriceIsOneThousand() {
            // arrange
            OrderItemEntity item = new OrderItemEntity(VALID_PRODUCT_ID, VALID_PRODUCT_NAME, 1000L, 3);

            // act & assert
            assertEquals(3000L, item.subtotal());
        }
    }
}
