package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryEntityTest {

    private static final Long VALID_PRODUCT_ID = 1L;
    private static final Integer VALID_QUANTITY = 10;

    @DisplayName("재고 생성")
    @Nested
    class Create {

        @DisplayName("유효한 productId와 양수 quantity로 생성하면 성공한다.")
        @Test
        void createsInventoryEntity_whenRequestIsValid() {
            // act
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // assert
            assertAll(
                    () -> assertEquals(VALID_PRODUCT_ID, inventory.getProductId()),
                    () -> assertEquals(VALID_QUANTITY, inventory.getQuantity())
            );
        }

        @DisplayName("quantity가 0이면 품절 상태로 정상 생성된다. (BVA)")
        @Test
        void createsInventoryEntity_whenQuantityIsZero() {
            // act
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, 0);

            // assert
            assertEquals(0, inventory.getQuantity());
        }

        @DisplayName("quantity가 1이면 정상 생성된다. (BVA)")
        @Test
        void createsInventoryEntity_whenQuantityIsOne() {
            // act
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, 1);

            // assert
            assertEquals(1, inventory.getQuantity());
        }

        @DisplayName("productId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            // act & assert
            assertThrows(CoreException.class, () -> new InventoryEntity(null, VALID_QUANTITY));
        }

        @DisplayName("quantity가 -1이면 예외가 발생한다. (BVA)")
        @Test
        void throwsException_whenQuantityIsMinusOne() {
            // act & assert
            assertThrows(CoreException.class, () -> new InventoryEntity(VALID_PRODUCT_ID, -1));
        }

        @DisplayName("quantity가 음수이면 예외가 발생한다.")
        @Test
        void throwsException_whenQuantityIsNegative() {
            // act & assert
            assertThrows(CoreException.class, () -> new InventoryEntity(VALID_PRODUCT_ID, -100));
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class Deduct {

        @DisplayName("amount가 quantity보다 작으면 차감 후 남은 재고가 반환된다.")
        @Test
        void deductsQuantity_whenAmountIsLessThanQuantity() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, 10);

            // act
            inventory.deduct(3);

            // assert
            assertEquals(7, inventory.getQuantity());
        }

        @DisplayName("amount가 quantity와 같으면 차감 후 재고가 0이 된다. (BVA)")
        @Test
        void deductsQuantityToZero_whenAmountEqualsQuantity() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, 10);

            // act
            inventory.deduct(10);

            // assert
            assertEquals(0, inventory.getQuantity());
        }

        @DisplayName("amount가 quantity보다 1 크면 재고 부족 예외가 발생한다. (BVA)")
        @Test
        void throwsException_whenAmountExceedsQuantityByOne() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, 10);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.deduct(11));
        }

        @DisplayName("amount가 quantity보다 크면 재고 부족 예외가 발생한다.")
        @Test
        void throwsException_whenAmountExceedsQuantity() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, 5);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.deduct(10));
        }

        @DisplayName("amount가 0이면 예외가 발생한다.")
        @Test
        void throwsException_whenAmountIsZero() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.deduct(0));
        }

        @DisplayName("amount가 음수이면 예외가 발생한다.")
        @Test
        void throwsException_whenAmountIsNegative() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.deduct(-1));
        }
    }

    @DisplayName("재고 수량 수정")
    @Nested
    class UpdateQuantity {

        @DisplayName("양수로 수정하면 quantity가 변경된다.")
        @Test
        void updatesQuantity_whenNewQuantityIsPositive() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act
            inventory.updateQuantity(20);

            // assert
            assertEquals(20, inventory.getQuantity());
        }

        @DisplayName("0으로 수정하면 quantity가 0이 된다. (품절, BVA)")
        @Test
        void updatesQuantityToZero_whenNewQuantityIsZero() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act
            inventory.updateQuantity(0);

            // assert
            assertEquals(0, inventory.getQuantity());
        }

        @DisplayName("1로 수정하면 정상 변경된다. (BVA)")
        @Test
        void updatesQuantity_whenNewQuantityIsOne() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act
            inventory.updateQuantity(1);

            // assert
            assertEquals(1, inventory.getQuantity());
        }

        @DisplayName("-1로 수정하면 예외가 발생한다. (BVA)")
        @Test
        void throwsException_whenNewQuantityIsMinusOne() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.updateQuantity(-1));
        }

        @DisplayName("음수로 수정하면 예외가 발생한다.")
        @Test
        void throwsException_whenNewQuantityIsNegative() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.updateQuantity(-100));
        }

        @DisplayName("null로 수정하면 예외가 발생한다.")
        @Test
        void throwsException_whenNewQuantityIsNull() {
            // arrange
            InventoryEntity inventory = new InventoryEntity(VALID_PRODUCT_ID, VALID_QUANTITY);

            // act & assert
            assertThrows(CoreException.class, () -> inventory.updateQuantity(null));
        }
    }
}
