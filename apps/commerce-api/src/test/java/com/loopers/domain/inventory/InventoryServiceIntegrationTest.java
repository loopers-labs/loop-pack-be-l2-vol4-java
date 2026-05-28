package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceIntegrationTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long OTHER_PRODUCT_ID = 2L;
    private static final Integer INITIAL_QUANTITY = 10;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 생성")
    @Nested
    class Create {

        @DisplayName("[ECP] 유효한 값으로 생성하면 id가 할당된 InventoryEntity가 반환된다.")
        @Test
        void createsInventory_whenRequestIsValid() {
            // act
            InventoryEntity result = inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(PRODUCT_ID, result.getProductId()),
                    () -> assertEquals(INITIAL_QUANTITY, result.getQuantity())
            );
        }

        @DisplayName("[ECP] quantity가 0이면 품절 상태로 정상 생성된다.")
        @Test
        void createsInventory_whenQuantityIsZero() {
            // act
            InventoryEntity result = inventoryService.create(PRODUCT_ID, 0);

            // assert
            assertEquals(0, result.getQuantity());
        }

        @DisplayName("[ECP] 음수 quantity이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.create(PRODUCT_ID, -1));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    @DisplayName("재고 단건 조회")
    @Nested
    class GetByProductId {

        @DisplayName("[ECP] 존재하는 productId로 조회하면 InventoryEntity가 반환된다.")
        @Test
        void returnsInventory_whenExists() {
            // arrange
            inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);

            // act
            InventoryEntity result = inventoryService.getByProductId(PRODUCT_ID);

            // assert
            assertAll(
                    () -> assertEquals(PRODUCT_ID, result.getProductId()),
                    () -> assertEquals(INITIAL_QUANTITY, result.getQuantity())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 productId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.getByProductId(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("재고 수량 수정")
    @Nested
    class UpdateQuantity {

        @DisplayName("[ECP] 유효한 quantity로 수정하면 변경이 반영된다.")
        @Test
        void updatesQuantity_whenValid() {
            // arrange
            inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);

            // act
            inventoryService.updateQuantity(PRODUCT_ID, 20);

            // assert
            assertEquals(20, inventoryService.getByProductId(PRODUCT_ID).getQuantity());
        }

        @DisplayName("[ECP] 존재하지 않는 productId이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.updateQuantity(999L, 10));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ECP] 음수 quantity이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // arrange
            inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.updateQuantity(PRODUCT_ID, -1));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class DeductAll {

        @DisplayName("[State Transition] 재고가 충분하면 차감 후 수량이 감소한다.")
        @Test
        void deductsQuantity_whenStockIsSufficient() {
            // arrange
            inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);
            inventoryService.create(OTHER_PRODUCT_ID, 5);

            // act
            inventoryService.deductAll(Map.of(PRODUCT_ID, 3, OTHER_PRODUCT_ID, 2));

            // assert
            assertAll(
                    () -> assertEquals(7, inventoryService.getByProductId(PRODUCT_ID).getQuantity()),
                    () -> assertEquals(3, inventoryService.getByProductId(OTHER_PRODUCT_ID).getQuantity())
            );
        }

        @DisplayName("[ECP] 재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            inventoryService.create(PRODUCT_ID, 2);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.deductAll(Map.of(PRODUCT_ID, 5)));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    @DisplayName("재고 단건 연쇄 삭제")
    @Nested
    class DeleteByProduct {

        @DisplayName("[State Transition] 삭제 후 해당 재고는 조회되지 않는다.")
        @Test
        void deletesInventory_thenNotFound() {
            // arrange
            inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);

            // act
            inventoryService.deleteByProduct(PRODUCT_ID);

            // assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.getByProductId(PRODUCT_ID));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("재고 복수 연쇄 삭제")
    @Nested
    class DeleteAllByProducts {

        @DisplayName("[State Transition] 복수 삭제 후 해당 재고들은 모두 조회되지 않는다.")
        @Test
        void deletesAllInventories_thenNotFound() {
            // arrange
            inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);
            inventoryService.create(OTHER_PRODUCT_ID, 5);

            // act
            inventoryService.deleteAllByProducts(java.util.List.of(PRODUCT_ID, OTHER_PRODUCT_ID));

            // assert
            assertAll(
                    () -> assertThrows(CoreException.class, () -> inventoryService.getByProductId(PRODUCT_ID)),
                    () -> assertThrows(CoreException.class, () -> inventoryService.getByProductId(OTHER_PRODUCT_ID))
            );
        }
    }
}
