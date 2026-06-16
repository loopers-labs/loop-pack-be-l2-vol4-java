package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long OTHER_PRODUCT_ID = 2L;
    private static final Integer INITIAL_QUANTITY = 10;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryEntity inventoryOf(Long id, Long productId, Integer quantity) {
        return InventoryEntity.of(id, productId, quantity, ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    @DisplayName("재고 생성")
    @Nested
    class Create {

        @DisplayName("[ECP] 유효한 값으로 생성하면 id가 할당된 InventoryEntity가 반환된다.")
        @Test
        void createsInventory_whenRequestIsValid() {
            // arrange
            InventoryEntity saved = inventoryOf(1L, PRODUCT_ID, INITIAL_QUANTITY);
            given(inventoryRepository.save(any())).willReturn(saved);

            // act
            InventoryEntity result = inventoryService.create(PRODUCT_ID, INITIAL_QUANTITY);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(PRODUCT_ID, result.getProductId()),
                    () -> assertEquals(INITIAL_QUANTITY, result.getQuantity())
            );
            verify(inventoryRepository).save(any());
        }

        @DisplayName("[ECP] quantity가 0이면 품절 상태로 정상 생성된다.")
        @Test
        void createsInventory_whenQuantityIsZero() {
            // arrange
            given(inventoryRepository.save(any())).willReturn(inventoryOf(1L, PRODUCT_ID, 0));

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
            InventoryEntity existing = inventoryOf(1L, PRODUCT_ID, INITIAL_QUANTITY);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));

            // act
            InventoryEntity result = inventoryService.getByProductId(PRODUCT_ID);

            // assert
            assertAll(
                    () -> assertEquals(PRODUCT_ID, result.getProductId()),
                    () -> assertEquals(INITIAL_QUANTITY, result.getQuantity())
            );
            verify(inventoryRepository).findByProductId(PRODUCT_ID);
        }

        @DisplayName("[ECP] 존재하지 않는 productId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // arrange
            given(inventoryRepository.findByProductId(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.getByProductId(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("재고 수량 수정")
    @Nested
    class UpdateQuantity {

        @DisplayName("[State Transition] 유효한 quantity로 수정하면 엔티티 수량이 변경된다.")
        @Test
        void updatesQuantity_whenValid() {
            // arrange
            InventoryEntity existing = inventoryOf(1L, PRODUCT_ID, INITIAL_QUANTITY);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));
            given(inventoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            inventoryService.updateQuantity(PRODUCT_ID, 20);

            // assert
            assertEquals(20, existing.getQuantity());
            verify(inventoryRepository).save(existing);
        }

        @DisplayName("[ECP] 존재하지 않는 productId이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            given(inventoryRepository.findByProductId(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.updateQuantity(999L, 10));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ECP] 음수 quantity이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // arrange
            InventoryEntity existing = inventoryOf(1L, PRODUCT_ID, INITIAL_QUANTITY);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(existing));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.updateQuantity(PRODUCT_ID, -1));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    @DisplayName("재고 차감")
    @Nested
    class DeductAll {

        @DisplayName("[State Transition] 재고가 충분하면 차감 후 엔티티 수량이 감소한다.")
        @Test
        void deductsQuantity_whenStockIsSufficient() {
            // arrange
            InventoryEntity entity1 = inventoryOf(1L, PRODUCT_ID, INITIAL_QUANTITY);
            InventoryEntity entity2 = inventoryOf(2L, OTHER_PRODUCT_ID, 5);
            given(inventoryRepository.findAllByProductIdsWithLock(any())).willReturn(List.of(entity1, entity2));
            given(inventoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            inventoryService.deductAll(Map.of(PRODUCT_ID, 3, OTHER_PRODUCT_ID, 2));

            // assert
            assertAll(
                    () -> assertEquals(7, entity1.getQuantity()),
                    () -> assertEquals(3, entity2.getQuantity())
            );
        }

        @DisplayName("[ECP] 재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            InventoryEntity entity = inventoryOf(1L, PRODUCT_ID, 2);
            given(inventoryRepository.findAllByProductIdsWithLock(any())).willReturn(List.of(entity));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.deductAll(Map.of(PRODUCT_ID, 5)));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] 존재하지 않는 productId가 포함되면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenInventoryNotExists() {
            // arrange (2개 요청에 1개 결과만 반환 → 크기 불일치)
            InventoryEntity entity1 = inventoryOf(1L, PRODUCT_ID, INITIAL_QUANTITY);
            given(inventoryRepository.findAllByProductIdsWithLock(any())).willReturn(List.of(entity1));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> inventoryService.deductAll(Map.of(PRODUCT_ID, 1, 999L, 1)));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("재고 단건 연쇄 삭제")
    @Nested
    class DeleteByProduct {

        @DisplayName("[State Transition] productId에 해당하는 재고 삭제 요청이 전달된다.")
        @Test
        void deletesInventory_byProductId() {
            // act
            inventoryService.deleteByProduct(PRODUCT_ID);

            // assert
            verify(inventoryRepository).deleteByProductId(PRODUCT_ID);
        }
    }

    @DisplayName("재고 복수 연쇄 삭제")
    @Nested
    class DeleteAllByProducts {

        @DisplayName("[State Transition] productIds에 해당하는 재고 삭제 요청이 전달된다.")
        @Test
        void deletesAllInventories_byProductIds() {
            // arrange
            List<Long> productIds = List.of(PRODUCT_ID, OTHER_PRODUCT_ID);

            // act
            inventoryService.deleteAllByProducts(productIds);

            // assert
            verify(inventoryRepository).deleteAllByProductIds(productIds);
        }
    }
}
