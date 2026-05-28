package com.loopers.domain.order;

import com.loopers.domain.vo.ShippingInfo;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.OrderFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, PENDING 상태 주문이 생성된다.")
        @Test
        void createsOrder_whenValid() {
            OrderModel order = OrderFixture.createModel(USER_ID);

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getPgAmount()).isEqualTo(0L),
                () -> assertThat(order.getItems()).isEmpty()
            );
        }
    }

    @DisplayName("주문 아이템을 추가할 때,")
    @Nested
    class AddItem {

        @DisplayName("아이템 추가 시, pgAmount가 소계만큼 증가한다.")
        @Test
        void increasesPgAmount_whenItemAdded() {
            OrderModel order = OrderFixture.createModel(USER_ID);
            OrderItemModel item = new OrderItemModel(PRODUCT_ID, ProductFixture.NAME, BrandFixture.NAME, ProductFixture.PRICE, 3);

            order.addItem(item);

            assertAll(
                () -> assertThat(order.getItems()).hasSize(1),
                () -> assertThat(order.getPgAmount()).isEqualTo(ProductFixture.PRICE * 3)
            );
        }

        @DisplayName("수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new OrderItemModel(PRODUCT_ID, ProductFixture.NAME, BrandFixture.NAME, ProductFixture.PRICE, 0)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태면, CONFIRMED 로 변경된다.")
        @Test
        void confirmsOrder_whenPending() {
            OrderModel order = OrderFixture.createModel(USER_ID);

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("PENDING 이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotPending() {
            OrderModel order = OrderFixture.createModel(USER_ID);
            order.confirm(); // CONFIRMED

            CoreException ex = assertThrows(CoreException.class, order::confirm);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태면, FAILED 로 변경된다.")
        @Test
        void failsOrder_whenPending() {
            OrderModel order = OrderFixture.createModel(USER_ID);

            order.fail();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @DisplayName("PENDING 이 아니면, 상태 변경 없이 멱등 처리된다.")
        @Test
        void isIdempotent_whenNotPending() {
            OrderModel order = OrderFixture.createModel(USER_ID);
            order.confirm(); // CONFIRMED

            order.fail(); // 멱등

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("CONFIRMED 상태면, CANCELLED 로 변경된다.")
        @Test
        void cancelsOrder_whenConfirmed() {
            OrderModel order = OrderFixture.createModel(USER_ID);
            order.confirm();

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("CONFIRMED 가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotConfirmed() {
            OrderModel order = OrderFixture.createModel(USER_ID); // PENDING

            CoreException ex = assertThrows(CoreException.class, order::cancel);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("배송지 정보를 생성할 때,")
    @Nested
    class ShippingInfoCreate {

        @DisplayName("필수 필드가 null/공백이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenReceiverNameIsBlank(String invalid) {
            CoreException ex = assertThrows(CoreException.class, () ->
                new ShippingInfo(invalid, "010-0000-0000", "12345", "서울시", null)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
