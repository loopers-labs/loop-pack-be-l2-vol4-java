package com.loopers.domain.order;

import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    private static final Long PRODUCT_ID = 100L;
    private static final String NAME = "에어맥스";

    @DisplayName("OrderItem 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, 주문 시점 스냅샷이 보존된다.")
        @Test
        void createsItem_whenValid() {
            // act
            OrderItemModel item = new OrderItemModel(PRODUCT_ID, NAME, Money.of(1000L), Quantity.of(2));

            // assert
            assertAll(
                () -> assertThat(item.getProductId()).isEqualTo(PRODUCT_ID),
                () -> assertThat(item.getProductNameSnapshot()).isEqualTo(NAME),
                () -> assertThat(item.getPriceSnapshot()).isEqualTo(1000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
            );
        }

        @DisplayName("수량이 1 이면, 정상 생성된다. (경계값)")
        @Test
        void createsItem_whenQuantityIsOne() {
            // act
            OrderItemModel item = new OrderItemModel(PRODUCT_ID, NAME, Money.of(1000L), Quantity.of(1));

            // assert
            assertThat(item.getQuantity()).isEqualTo(1);
        }

        @DisplayName("수량이 0 이면, BAD_REQUEST 예외가 발생한다. (주문은 1개 이상)")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(PRODUCT_ID, NAME, Money.of(1000L), Quantity.of(0)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(null, NAME, Money.of(1000L), Quantity.of(1)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명 스냅샷이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameBlank() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(PRODUCT_ID, " ", Money.of(1000L), Quantity.of(1)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격 스냅샷이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceSnapshotIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(PRODUCT_ID, NAME, null, Quantity.of(1)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new OrderItemModel(PRODUCT_ID, NAME, Money.of(1000L), null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소계를 계산할 때, ")
    @Nested
    class Subtotal {

        @DisplayName("단가 × 수량을 반환한다.")
        @Test
        void returnsPriceTimesQuantity() {
            // arrange
            OrderItemModel item = new OrderItemModel(PRODUCT_ID, NAME, Money.of(1000L), Quantity.of(3));

            // act + assert
            assertThat(item.subtotal().getAmount()).isEqualTo(3000L);
        }
    }
}
