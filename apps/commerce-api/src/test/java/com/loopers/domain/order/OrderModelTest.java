package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private static OrderItemModel sampleItem() {
        return new OrderItemModel(1L, "테스트 상품", BigDecimal.valueOf(10000), 1L);
    }

    @DisplayName("주문 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력으로 생성하면 status가 PLACED로 초기화된다.")
        @Test
        void createsOrderModel_withPlacedStatus() {
            // given
            Long userId = 1L;
            BigDecimal totalPrice = BigDecimal.valueOf(10000);
            List<OrderItemModel> items = List.of(sampleItem());

            // when
            OrderModel order = new OrderModel(userId, totalPrice, items);

            // then
            assertAll(
                    () -> assertThat(order.getUserId()).isEqualTo(userId),
                    () -> assertThat(order.getTotalPrice()).isEqualByComparingTo(totalPrice),
                    () -> assertThat(order.getItems()).hasSize(1),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED)
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenUserIdIsNull(Long userId) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(userId, BigDecimal.valueOf(10000), List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalPrice가 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenTotalPriceIsNull(BigDecimal totalPrice) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, totalPrice, List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalPrice가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -10000L})
        @ParameterizedTest
        void throwsBadRequest_whenTotalPriceIsNegative(long totalPrice) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(totalPrice), List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(10000), null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 빈 리스트이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(10000), List.of()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("create() 팩토리로 주문 모델을 생성할 때,")
    @Nested
    class CreateFactory {

        @DisplayName("여러 항목의 totalPrice가 각 항목의 가격 × 수량 합산으로 계산된다.")
        @Test
        void calculatesTotalPrice_fromItemDataList() {
            // given
            Long userId = 1L;
            List<OrderItemData> itemDataList = List.of(
                    new OrderItemData(1L, "상품A", BigDecimal.valueOf(10000), 2L),
                    new OrderItemData(2L, "상품B", BigDecimal.valueOf(5000), 3L)
            );

            // when
            OrderModel order = OrderModel.create(userId, itemDataList);

            // then
            assertAll(
                    () -> assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(35000)),
                    () -> assertThat(order.getItems()).hasSize(2),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED)
            );
        }
    }

    @DisplayName("주문 소유자를 검증할 때,")
    @Nested
    class ValidateOwner {

        @DisplayName("주문 소유자 ID와 일치하면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOwnerIdMatches() {
            // given
            Long userId = 1L;
            OrderModel order = new OrderModel(userId, BigDecimal.valueOf(10000), List.of(sampleItem()));

            // when & then
            assertDoesNotThrow(() -> order.validateOwner(userId));
        }

        @DisplayName("다른 사용자 ID이면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenUserIdDoesNotMatch() {
            // given
            OrderModel order = new OrderModel(1L, BigDecimal.valueOf(10000), List.of(sampleItem()));

            // when
            CoreException result = assertThrows(CoreException.class, () -> order.validateOwner(2L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
