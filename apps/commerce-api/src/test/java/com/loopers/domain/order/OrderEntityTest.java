package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderEntityTest {

    private static final Long VALID_USER_ID = 1L;

    private OrderSnapshotItem item(Long productId) {
        return new OrderSnapshotItem(productId, "상품명", 10000L, 1, 10000L);
    }

    private OrderSnapshot validSnapshot(Long productId) {
        return new OrderSnapshot(List.of(item(productId)), 10000L, 0L, 10000L, null);
    }

    @DisplayName("주문 생성")
    @Nested
    class Create {

        @DisplayName("유효한 userId와 snapshot으로 생성하면 성공한다.")
        @Test
        void createsOrderEntity_whenRequestIsValid() {
            // arrange
            OrderSnapshot snapshot = validSnapshot(1L);

            // act
            OrderEntity order = new OrderEntity(VALID_USER_ID, snapshot);

            // assert
            assertAll(
                    () -> assertEquals(VALID_USER_ID, order.getUserId()),
                    () -> assertEquals(OrderStatus.PENDING, order.getStatus()),
                    () -> assertEquals(snapshot, order.getSnapshot())
            );
        }

        @DisplayName("생성 시 status는 PENDING이다.")
        @Test
        void createsOrderEntity_withPendingStatus() {
            // act
            OrderEntity order = new OrderEntity(VALID_USER_ID, validSnapshot(1L));

            // assert
            assertEquals(OrderStatus.PENDING, order.getStatus());
        }

        @DisplayName("userId가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(null, validSnapshot(1L)));
        }

        @DisplayName("snapshot이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenSnapshotIsNull() {
            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(VALID_USER_ID, null));
        }

        @DisplayName("snapshot의 items가 빈 배열이면 예외가 발생한다.")
        @Test
        void throwsException_whenSnapshotItemsIsEmpty() {
            // arrange
            OrderSnapshot emptySnapshot = new OrderSnapshot(List.of(), 0L, 0L, 0L, null);

            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(VALID_USER_ID, emptySnapshot));
        }

        @DisplayName("snapshot items 내 중복 productId가 있으면 예외가 발생한다.")
        @Test
        void throwsException_whenSnapshotItemsHasDuplicateProductId() {
            // arrange
            OrderSnapshot duplicateSnapshot = new OrderSnapshot(
                    List.of(item(1L), item(1L)), 20000L, 0L, 20000L, null);

            // act & assert
            assertThrows(CoreException.class, () -> new OrderEntity(VALID_USER_ID, duplicateSnapshot));
        }
    }

    @DisplayName("finalAmount 조회")
    @Nested
    class FinalAmount {

        @DisplayName("쿠폰 없는 주문의 finalAmount는 originalAmount와 같다.")
        @Test
        void returnsFinalAmount_equalToOriginalAmount_whenNoCoupon() {
            // arrange
            OrderSnapshot snapshot = new OrderSnapshot(List.of(item(1L)), 10000L, 0L, 10000L, null);
            OrderEntity order = new OrderEntity(VALID_USER_ID, snapshot);

            // act & assert
            assertEquals(10000L, order.finalAmount());
        }

        @DisplayName("쿠폰 적용 주문의 finalAmount는 originalAmount - discountAmount이다.")
        @Test
        void returnsFinalAmount_equalToDiscountedAmount_whenCouponApplied() {
            // arrange
            OrderSnapshot snapshot = new OrderSnapshot(List.of(item(1L)), 10000L, 1000L, 9000L, 42L);
            OrderEntity order = new OrderEntity(VALID_USER_ID, snapshot);

            // act & assert
            assertEquals(9000L, order.finalAmount());
        }
    }

    @DisplayName("주문 소유권 검증")
    @Nested
    class IsOwnedBy {

        @DisplayName("소유자 userId와 일치하면 true를 반환한다.")
        @Test
        void returnsTrue_whenUserIdMatches() {
            OrderEntity order = new OrderEntity(VALID_USER_ID, validSnapshot(1L));
            assertTrue(order.isOwnedBy(VALID_USER_ID));
        }

        @DisplayName("다른 userId이면 false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdDoesNotMatch() {
            OrderEntity order = new OrderEntity(VALID_USER_ID, validSnapshot(1L));
            assertFalse(order.isOwnedBy(2L));
        }

        @DisplayName("null이면 false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdIsNull() {
            OrderEntity order = new OrderEntity(VALID_USER_ID, validSnapshot(1L));
            assertFalse(order.isOwnedBy(null));
        }
    }
}
