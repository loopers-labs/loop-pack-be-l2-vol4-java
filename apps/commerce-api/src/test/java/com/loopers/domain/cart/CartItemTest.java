package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartItemTest {

    @DisplayName("장바구니 항목을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 수량이면 정상 생성된다.")
        @Test
        void creates_whenQuantityIsValid() {
            CartItem item = new CartItem(1L, 1L, 3);
            assertThat(item.getQuantity()).isEqualTo(3);
        }

        @DisplayName("수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CartItem(1L, 1L, 0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CartItem(1L, 1L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("수량을 변경할 때,")
    @Nested
    class UpdateQuantity {

        @DisplayName("유효한 수량이면 변경된다.")
        @Test
        void updates_whenQuantityIsValid() {
            CartItem item = new CartItem(1L, 1L, 3);
            item.updateQuantity(5);
            assertThat(item.getQuantity()).isEqualTo(5);
        }

        @DisplayName("수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CartItem item = new CartItem(1L, 1L, 3);
            CoreException result = assertThrows(CoreException.class,
                () -> item.updateQuantity(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("수량을 증가시킬 때,")
    @Nested
    class IncreaseQuantity {

        @DisplayName("기존 수량에 추가된다.")
        @Test
        void increases_correctly() {
            CartItem item = new CartItem(1L, 1L, 3);
            item.increaseQuantity(2);
            assertThat(item.getQuantity()).isEqualTo(5);
        }
    }

    @DisplayName("소유권을 확인할 때,")
    @Nested
    class BelongsTo {

        @DisplayName("본인 항목이면 true를 반환한다.")
        @Test
        void returnsTrue_whenOwner() {
            CartItem item = new CartItem(1L, 1L, 3);
            assertThat(item.belongsTo(1L)).isTrue();
        }

        @DisplayName("타인 항목이면 false를 반환한다.")
        @Test
        void returnsFalse_whenNotOwner() {
            CartItem item = new CartItem(1L, 1L, 3);
            assertThat(item.belongsTo(2L)).isFalse();
        }
    }
}
