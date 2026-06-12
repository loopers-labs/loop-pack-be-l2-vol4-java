package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderModelTest {

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 memberId, totalPrice로 생성하면, PENDING 상태로 생성된다.")
        @Test
        void createsOrder_whenValidInput() {
            // act
            OrderModel order = new OrderModel(1L, 10000L);

            // assert
            assertThat(order.getMemberId()).isEqualTo(1L);
            assertThat(order.getTotalPrice()).isEqualTo(10000L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenMemberIdIsNull() {
            assertThatThrownBy(() -> new OrderModel(null, 10000L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalPrice가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenTotalPriceIsNegative() {
            assertThatThrownBy(() -> new OrderModel(1L, -1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 적용해 주문을 생성할 때,")
    @Nested
    class CreateWithCoupon {

        @DisplayName("쿠폰 없이 생성 시 discountAmount=0, totalPrice=originalAmount이다.")
        @Test
        void noCoupon_totalPriceEqualsOriginalAmount() {
            // act
            OrderModel order = new OrderModel(1L, null, 10000L, 0L);

            // assert
            assertThat(order.getOriginalAmount()).isEqualTo(10000L);
            assertThat(order.getDiscountAmount()).isEqualTo(0L);
            assertThat(order.getTotalPrice()).isEqualTo(10000L);
        }

        @DisplayName("할인 금액 적용 시 totalPrice = originalAmount - discountAmount이다.")
        @Test
        void withCoupon_totalPriceIsReduced() {
            // act
            OrderModel order = new OrderModel(1L, 1L, 10000L, 1000L);

            // assert
            assertThat(order.getOriginalAmount()).isEqualTo(10000L);
            assertThat(order.getDiscountAmount()).isEqualTo(1000L);
            assertThat(order.getTotalPrice()).isEqualTo(9000L);
        }

        @DisplayName("discountAmount가 음수이면 BAD_REQUEST가 발생한다.")
        @Test
        void negativeDiscount_throwsBadRequest() {
            assertThatThrownBy(() -> new OrderModel(1L, null, 10000L, -1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("PENDING 상태에서 cancel()을 호출하면, CANCELLED로 변경된다.")
        @Test
        void cancel_changeStatusToCancelled() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L);

            // act
            order.cancel();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 CANCELLED 상태에서 cancel()을 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void cancel_throwsException_whenAlreadyCancelled() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L);
            order.cancel();

            // act & assert
            assertThatThrownBy(order::cancel)
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태에서 confirm()을 호출하면, CONFIRMED로 변경된다.")
        @Test
        void confirm_changeStatusToConfirmed() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L);

            // act
            order.confirm();

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @DisplayName("주문 소유자를 확인할 때,")
    @Nested
    class IsOwnedBy {

        @DisplayName("본인 주문이면 true를 반환한다.")
        @Test
        void isOwnedBy_returnsTrue_whenSameMember() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L);

            // assert
            assertThat(order.isOwnedBy(1L)).isTrue();
        }
    }
}
