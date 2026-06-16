package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderEntityTest {

    private static final Long VALID_USER_ID = 1L;

    private OrderItemEntity createItem(Long productId) {
        return new OrderItemEntity(productId, "상품명", 10000L, 1);
    }

    @DisplayName("주문 생성")
    @Nested
    class Create {

        @DisplayName("유효한 userId와 items로 생성하면 성공한다.")
        @Test
        void createsOrderEntity_whenRequestIsValid() {
            // arrange
            List<OrderItemEntity> items = List.of(createItem(1L));

            // act
            OrderEntity order = new OrderEntity(VALID_USER_ID, items);

            // assert
            assertAll(
                    () -> assertEquals(VALID_USER_ID, order.getUserId()),
                    () -> assertEquals(1, order.getItems().size())
            );
        }

        @DisplayName("생성 시 status는 PENDING이다.")
        @Test
        void createsOrderEntity_withPendingStatus() {
            // arrange
            List<OrderItemEntity> items = List.of(createItem(1L));

            // act
            OrderEntity order = new OrderEntity(VALID_USER_ID, items);

            // assert
            assertEquals(OrderStatus.PENDING, order.getStatus());
        }

        @DisplayName("userId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            // arrange
            List<OrderItemEntity> items = List.of(createItem(1L));

            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(null, items));
        }

        @DisplayName("items가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenItemsIsNull() {
            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(VALID_USER_ID, null));
        }

        @DisplayName("items가 빈 배열이면 예외가 발생한다.")
        @Test
        void throwsException_whenItemsIsEmpty() {
            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(VALID_USER_ID, List.of()));
        }

        @DisplayName("items 내 중복 productId가 있으면 예외가 발생한다.")
        @Test
        void throwsException_whenItemsHasDuplicateProductId() {
            // arrange
            List<OrderItemEntity> items = List.of(createItem(1L), createItem(1L));

            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(VALID_USER_ID, items));
        }
    }

    @DisplayName("총 주문 금액 계산")
    @Nested
    class CalculateTotalAmount {

        @DisplayName("단일 아이템의 totalAmount는 해당 아이템의 subtotal과 같다.")
        @Test
        void returnsSingleItemSubtotal_whenOneItem() {
            // arrange
            OrderItemEntity item = new OrderItemEntity(1L, "상품A", 10000L, 2);
            OrderEntity order = new OrderEntity(VALID_USER_ID, List.of(item));

            // act & assert
            assertEquals(item.subtotal(), order.calculateTotalAmount());
        }

        @DisplayName("복수 아이템의 totalAmount는 각 subtotal의 합산이다.")
        @Test
        void returnsSumOfSubtotals_whenMultipleItems() {
            // arrange
            OrderItemEntity item1 = new OrderItemEntity(1L, "상품A", 10000L, 2);
            OrderItemEntity item2 = new OrderItemEntity(2L, "상품B", 5000L, 3);
            OrderEntity order = new OrderEntity(VALID_USER_ID, List.of(item1, item2));

            // act & assert
            assertEquals(35000L, order.calculateTotalAmount());
        }
    }

    @DisplayName("주문 소유권 검증")
    @Nested
    class IsOwnedBy {

        @DisplayName("소유자 userId와 일치하면 true를 반환한다.")
        @Test
        void returnsTrue_whenUserIdMatches() {
            // arrange
            OrderEntity order = new OrderEntity(VALID_USER_ID, List.of(createItem(1L)));

            // act & assert
            assertTrue(order.isOwnedBy(VALID_USER_ID));
        }

        @DisplayName("다른 userId이면 false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdDoesNotMatch() {
            // arrange
            OrderEntity order = new OrderEntity(VALID_USER_ID, List.of(createItem(1L)));

            // act & assert
            assertFalse(order.isOwnedBy(2L));
        }

        @DisplayName("null이면 false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdIsNull() {
            // arrange
            OrderEntity order = new OrderEntity(VALID_USER_ID, List.of(createItem(1L)));

            // act & assert
            assertFalse(order.isOwnedBy(null));
        }
    }
}
