package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryTest {

    private static final Long PRODUCT_ID = 1L;

    @DisplayName("Inventory 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상의 수량이면 productId 와 재고를 가진 Inventory 가 생성된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100, Integer.MAX_VALUE})
        void createsInventory_whenValid(int quantity) {
            Inventory inventory = Inventory.create(PRODUCT_ID, quantity);

            assertThat(inventory.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(inventory.getQuantity()).isEqualTo(quantity);
        }

        @DisplayName("productId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Inventory.create(null, 10));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityNegative() {
            CoreException result = assertThrows(CoreException.class,
                    () -> Inventory.create(PRODUCT_ID, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("decrease 로 재고를 차감할 때, ")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분하면 수량만큼 차감된다.")
        @Test
        void decreasesQuantity_whenEnough() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);

            inventory.decrease(3);

            assertThat(inventory.getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생하고 수량은 변하지 않는다.")
        @Test
        void throwsBadRequest_whenNotEnough() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 2);

            CoreException result = assertThrows(CoreException.class, () -> inventory.decrease(3));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(inventory.getQuantity()).isEqualTo(2);
        }

        @DisplayName("차감 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsBadRequest_whenQtyNotPositive(int qty) {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);

            CoreException result = assertThrows(CoreException.class, () -> inventory.decrease(qty));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(inventory.getQuantity()).isEqualTo(10);
        }
    }

    @DisplayName("adjust 로 재고를 절대값으로 설정할 때, ")
    @Nested
    class Adjust {

        @DisplayName("0 이상의 새 수량으로 교체된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 5, 100})
        void adjustsToAbsoluteQuantity(int newQuantity) {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);

            inventory.adjust(newQuantity);

            assertThat(inventory.getQuantity()).isEqualTo(newQuantity);
        }

        @DisplayName("새 수량이 음수이면 BAD_REQUEST 예외가 발생하고 수량은 변하지 않는다.")
        @Test
        void throwsBadRequest_whenNegative() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);

            CoreException result = assertThrows(CoreException.class, () -> inventory.adjust(-1));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(inventory.getQuantity()).isEqualTo(5);
        }
    }

    @DisplayName("isSoldOut 은 재고가 0 일 때만 true 다.")
    @Test
    void isSoldOut() {
        assertThat(Inventory.create(PRODUCT_ID, 0).isSoldOut()).isTrue();
        assertThat(Inventory.create(PRODUCT_ID, 1).isSoldOut()).isFalse();
    }
}
