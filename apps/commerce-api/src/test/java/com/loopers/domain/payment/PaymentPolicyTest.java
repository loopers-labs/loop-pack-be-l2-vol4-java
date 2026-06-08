package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentPolicyTest {

    private PaymentPolicy sut;

    @BeforeEach
    void setUp() {
        sut = new PaymentPolicy();
    }

    private OrderModel requestedOrder(ZonedDateTime createdAt) {
        OrderModel order = new OrderModel(1L);
        setCreatedAt(order, createdAt);
        return order;
    }

    private void setCreatedAt(OrderModel order, ZonedDateTime createdAt) {
        try {
            Field field = BaseEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(order, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DisplayName("결제 가능 여부 검증 시,")
    @Nested
    class ValidatePayable {

        @DisplayName("주문 상태가 REQUESTED가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderStatusIsNotRequested() {
            OrderModel order = new OrderModel(1L);
            order.complete();
            ZonedDateTime now = ZonedDateTime.now();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.validatePayable(order, now));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("결제 가능 시간(15분)이 초과되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPaymentWindowExpired() {
            OrderModel order = requestedOrder(ZonedDateTime.now().minusMinutes(16));
            ZonedDateTime now = ZonedDateTime.now();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.validatePayable(order, now));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 상태가 REQUESTED이고 15분 이내이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOrderIsValidAndWithinPaymentWindow() {
            OrderModel order = requestedOrder(ZonedDateTime.now().minusMinutes(5));
            ZonedDateTime now = ZonedDateTime.now();

            assertThatNoException().isThrownBy(() -> sut.validatePayable(order, now));
        }
    }

    @DisplayName("결제 만료 가능 여부 검증 시,")
    @Nested
    class ValidateExpirable {

        @DisplayName("주문 상태가 REQUESTED가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderStatusIsNotRequested() {
            OrderModel order = requestedOrder(ZonedDateTime.now().minusMinutes(20));
            order.complete();
            ZonedDateTime now = ZonedDateTime.now();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.validateExpirable(order, now));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("결제 만료 가능 시간(15분)이 지나지 않았으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPaymentWindowNotYetExpired() {
            OrderModel order = requestedOrder(ZonedDateTime.now().minusMinutes(5));
            ZonedDateTime now = ZonedDateTime.now();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.validateExpirable(order, now));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 상태가 REQUESTED이고 15분이 경과했으면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenOrderIsValidAndPaymentWindowExpired() {
            OrderModel order = requestedOrder(ZonedDateTime.now().minusMinutes(16));
            ZonedDateTime now = ZonedDateTime.now();

            assertThatNoException().isThrownBy(() -> sut.validateExpirable(order, now));
        }
    }
}
