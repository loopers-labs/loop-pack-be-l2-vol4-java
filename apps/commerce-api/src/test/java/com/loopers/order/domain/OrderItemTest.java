package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderItemTest {

    private OrderItem snapshot() {
        return OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2);
    }

    @Test
    @DisplayName("create 로 생성하면 주문 당시 상품 스냅샷이 저장되고 orderId 는 아직 비어 있다")
    void givenSnapshotFields_whenCreate_thenStoresFieldsWithoutOrderId() {
        OrderItem item = snapshot();

        assertAll(
                () -> assertThat(item.getOrderId()).isNull(),
                () -> assertThat(item.getProductId()).isEqualTo(10L),
                () -> assertThat(item.getProductName()).isEqualTo("셔츠"),
                () -> assertThat(item.getBrandId()).isEqualTo(1L),
                () -> assertThat(item.getBrandName()).isEqualTo("루퍼스"),
                () -> assertThat(item.getPrice()).isEqualTo(29_000L),
                () -> assertThat(item.getQuantity()).isEqualTo(2)
        );
    }

    @Test
    @DisplayName("subtotal 은 가격 * 수량이다")
    void givenPriceAndQuantity_whenSubtotal_thenReturnsProduct() {
        OrderItem item = OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 3);

        assertThat(item.subtotal()).isEqualTo(87_000L);
    }

    @Test
    @DisplayName("assignOrder 로 orderId 를 채울 수 있다")
    void givenOrderId_whenAssignOrder_thenStoresOrderId() {
        OrderItem item = snapshot();

        item.assignOrder(100L);

        assertThat(item.getOrderId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("productId 가 null 이면 CoreException 이 발생한다")
    void givenNullProductId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> OrderItem.create(null, "셔츠", 1L, "루퍼스", 29_000L, 2))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("productId 는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("상품명 스냅샷이 비어 있으면 CoreException 이 발생한다")
    void givenBlankProductName_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> OrderItem.create(10L, invalid, 1L, "루퍼스", 29_000L, 2))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품명은 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("brandId 가 null 이면 CoreException 이 발생한다")
    void givenNullBrandId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> OrderItem.create(10L, "셔츠", null, "루퍼스", 29_000L, 2))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("brandId 는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("브랜드명 스냅샷이 비어 있으면 CoreException 이 발생한다")
    void givenBlankBrandName_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> OrderItem.create(10L, "셔츠", 1L, invalid, 29_000L, 2))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드명은 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -1000L})
    @DisplayName("가격 스냅샷이 음수이면 CoreException 이 발생한다")
    void givenNegativePrice_whenCreate_thenThrowsCoreException(long invalid) {
        assertThatThrownBy(() -> OrderItem.create(10L, "셔츠", 1L, "루퍼스", invalid, 2))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("가격은 0 이상이어야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("주문 수량이 1 미만이면 CoreException 이 발생한다")
    void givenNonPositiveQuantity_whenCreate_thenThrowsCoreException(int invalid) {
        assertThatThrownBy(() -> OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, invalid))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("주문 수량은 1 이상이어야 합니다.");
    }
}
