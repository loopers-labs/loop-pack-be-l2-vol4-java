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
            BigDecimal originalPrice = BigDecimal.valueOf(10000);
            List<OrderItemModel> items = List.of(sampleItem());

            // when
            OrderModel order = new OrderModel(userId, originalPrice, BigDecimal.ZERO, items);

            // then
            assertAll(
                    () -> assertThat(order.getUserId()).isEqualTo(userId),
                    () -> assertThat(order.getOriginalPrice()).isEqualByComparingTo(originalPrice),
                    () -> assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(order.getFinalPrice()).isEqualByComparingTo(originalPrice),
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
                    () -> new OrderModel(userId, BigDecimal.valueOf(10000), BigDecimal.ZERO, List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("originalPrice가 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenOriginalPriceIsNull(BigDecimal originalPrice) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, originalPrice, BigDecimal.ZERO, List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("originalPrice가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -10000L})
        @ParameterizedTest
        void throwsBadRequest_whenOriginalPriceIsNegative(long originalPrice) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(originalPrice), BigDecimal.ZERO, List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("discountAmount가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -1000L})
        @ParameterizedTest
        void throwsBadRequest_whenDiscountAmountIsNegative(long discountAmount) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.valueOf(discountAmount), List.of(sampleItem())));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.ZERO, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("items가 빈 리스트이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.ZERO, List.of()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("create() 팩토리로 주문 모델을 생성할 때,")
    @Nested
    class CreateFactory {

        @DisplayName("여러 항목의 originalPrice가 각 항목의 가격 × 수량 합산으로 계산된다.")
        @Test
        void calculatesOriginalPrice_fromItemDataList() {
            // given
            Long userId = 1L;
            List<OrderItemData> itemDataList = List.of(
                    new OrderItemData(1L, "상품A", BigDecimal.valueOf(10000), 2L),
                    new OrderItemData(2L, "상품B", BigDecimal.valueOf(5000), 3L)
            );

            // when
            OrderModel order = OrderModel.create(userId, itemDataList, BigDecimal.ZERO);

            // then
            assertAll(
                    () -> assertThat(order.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(35000)),
                    () -> assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(order.getFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(35000)),
                    () -> assertThat(order.getItems()).hasSize(2),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED)
            );
        }

        @DisplayName("할인 금액이 있으면 finalPrice는 originalPrice에서 discountAmount를 뺀 값이다.")
        @Test
        void calculatesFinalPrice_withDiscountAmount() {
            // given
            Long userId = 1L;
            List<OrderItemData> itemDataList = List.of(
                    new OrderItemData(1L, "상품A", BigDecimal.valueOf(10000), 1L)
            );

            // when
            OrderModel order = OrderModel.create(userId, itemDataList, BigDecimal.valueOf(2000));

            // then
            assertAll(
                    () -> assertThat(order.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000)),
                    () -> assertThat(order.getFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(8000))
            );
        }

        @DisplayName("orderNumber가 타임스탬프(14자) + 난수(6자) 형식으로 생성된다.")
        @Test
        void generatesOrderNumber_withTimestampAndRandomSuffix() {
            // given
            Long userId = 1L;
            List<OrderItemData> itemDataList = List.of(
                    new OrderItemData(1L, "상품A", BigDecimal.valueOf(10000), 1L)
            );

            // when
            OrderModel order = OrderModel.create(userId, itemDataList, BigDecimal.ZERO);

            // then
            assertThat(order.getOrderNumber()).matches("\\d{20}");
        }

        @DisplayName("호출마다 서로 다른 orderNumber가 생성된다.")
        @Test
        void generatesDifferentOrderNumber_perCall() {
            // given
            Long userId = 1L;
            List<OrderItemData> itemDataList = List.of(
                    new OrderItemData(1L, "상품A", BigDecimal.valueOf(10000), 1L)
            );

            // when
            OrderModel order1 = OrderModel.create(userId, itemDataList, BigDecimal.ZERO);
            OrderModel order2 = OrderModel.create(userId, itemDataList, BigDecimal.ZERO);

            // then
            assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
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
            OrderModel order = new OrderModel(userId, BigDecimal.valueOf(10000), BigDecimal.ZERO, List.of(sampleItem()));

            // when & then
            assertDoesNotThrow(() -> order.validateOwner(userId));
        }

        @DisplayName("다른 사용자 ID이면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenUserIdDoesNotMatch() {
            // given
            OrderModel order = new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.ZERO, List.of(sampleItem()));

            // when
            CoreException result = assertThrows(CoreException.class, () -> order.validateOwner(2L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
