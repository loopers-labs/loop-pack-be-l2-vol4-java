package com.loopers.domain.order;

import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrderModelTest {

    private static final Long USER_ID = 1L;

    private OrderItemModel item(long unitPrice, int qty) {
        return OrderItemModel.of(100L, Quantity.of(qty), Money.of(unitPrice), "운동화", "Nike", "img.png");
    }

    @DisplayName("주문 생성 시 총액은 각 항목 소계(단가×수량)의 합이다.")
    @Test
    void create_calculatesTotalAmountFromItems() {
        // 1000×2 + 3000×1 = 5000
        OrderModel order = OrderModel.create(USER_ID, List.of(item(1000L, 2), item(3000L, 1)));

        assertThat(order.getTotalAmount()).isEqualTo(Money.of(5000L));
    }

    @DisplayName("주문은 생성 직후 PENDING 상태다.")
    @Test
    void create_startsWithPendingStatus() {
        OrderModel order = OrderModel.create(USER_ID, List.of(item(1000L, 1)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("userId 가 null 이면 BAD_REQUEST.")
    @Test
    void create_throwsBadRequest_whenUserIdIsNull() {
        CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(null, List.of(item(1000L, 1))));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("주문 항목이 비어있으면 BAD_REQUEST.")
    @Test
    void create_throwsBadRequest_whenItemsAreEmpty() {
        CoreException result = assertThrows(CoreException.class,
                () -> OrderModel.create(USER_ID, List.of()));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("getItems 로 받은 컬렉션은 외부에서 수정할 수 없다.")
    @Test
    void getItems_returnsUnmodifiableList() {
        OrderModel order = OrderModel.create(USER_ID, List.of(item(1000L, 1)));

        assertThatThrownBy(() -> order.getItems().add(item(5000L, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("confirm 하면 COMPLETED, fail 하면 FAILED 로 전이된다.")
    @Test
    void statusTransition_confirmAndFail() {
        OrderModel confirmed = OrderModel.create(USER_ID, List.of(item(1000L, 1)));
        confirmed.confirm();
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        OrderModel failed = OrderModel.create(USER_ID, List.of(item(1000L, 1)));
        failed.fail();
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @DisplayName("쿠폰 미적용 주문은 할인액 0, 최종금액 = 총액, 적용쿠폰 없음이다.")
    @Test
    void create_withoutCoupon_finalEqualsTotal() {
        OrderModel order = OrderModel.create(USER_ID, List.of(item(1000L, 2)));   // total 2000

        assertThat(order.getTotalAmount()).isEqualTo(Money.of(2000L));
        assertThat(order.getDiscountAmount()).isEqualTo(Money.ZERO);
        assertThat(order.getFinalAmount()).isEqualTo(Money.of(2000L));
        assertThat(order.getUserCouponId()).isEmpty();
    }

    @DisplayName("쿠폰을 적용하면 최종금액 = 총액 - 할인액이고, 할인 스냅샷이 남는다.")
    @Test
    void applyCoupon_setsDiscountAndFinalAmount() {
        OrderModel order = OrderModel.create(USER_ID, List.of(item(1000L, 2), item(3000L, 1)));  // total 5000

        order.applyCoupon(42L, Money.of(500L));

        assertThat(order.getTotalAmount()).isEqualTo(Money.of(5000L));   // 할인 전
        assertThat(order.getDiscountAmount()).isEqualTo(Money.of(500L)); // 할인액
        assertThat(order.getFinalAmount()).isEqualTo(Money.of(4500L));   // 최종
        assertThat(order.getUserCouponId()).contains(42L);
    }
}
