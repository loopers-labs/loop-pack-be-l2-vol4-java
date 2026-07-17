package com.loopers.inventory.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryTest {

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {
        @DisplayName("상품이 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdNull() {
            CoreException result =
                assertThrows(CoreException.class, () -> new Inventory(null, 10));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityNegative() {
            CoreException result =
                assertThrows(CoreException.class, () -> new Inventory(1L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Deduct {
        @DisplayName("재고가 충분하면 수량만큼 차감된다.")
        @Test
        void deducts_whenEnough() {
            Inventory inventory = new Inventory(1L, 10);
            inventory.deduct(3);
            assertThat(inventory.getAvailableQuantity()).isEqualTo(7);
        }

        @DisplayName("재고를 정확히 모두 차감하면 0이 되며 음수가 되지 않는다.")
        @Test
        void allowsExactDeduction() {
            Inventory inventory = new Inventory(1L, 5);
            inventory.deduct(5);
            assertThat(inventory.getAvailableQuantity()).isZero();
        }

        @DisplayName("재고보다 많은 수량이면 CONFLICT 예외가 발생하고 재고는 변하지 않는다.")
        @Test
        void throwsConflict_whenInsufficient() {
            Inventory inventory = new Inventory(1L, 2);
            CoreException result = assertThrows(CoreException.class, () -> inventory.deduct(3));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(inventory.getAvailableQuantity()).isEqualTo(2);
        }

        @DisplayName("0 이하 수량이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotPositive() {
            Inventory inventory = new Inventory(1L, 10);
            CoreException result = assertThrows(CoreException.class, () -> inventory.deduct(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 복구하면 수량만큼 증가한다.")
    @Test
    void restore() {
        Inventory inventory = new Inventory(1L, 5);
        inventory.deduct(5);
        inventory.restore(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(2);
    }

    @DisplayName("재고를 절대값으로 변경할 수 있다.")
    @Test
    void changeQuantity() {
        Inventory inventory = new Inventory(1L, 5);
        inventory.changeQuantity(20);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(20);
    }
}
