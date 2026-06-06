package com.loopers.domain.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;

@Component
public class PaymentPolicy {

    private static final int PAYMENT_EXPIRY_MINUTES = 15;

    public void validatePayable(OrderModel order, ZonedDateTime now) {
        if (!order.isPayable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
        }
        if (Duration.between(order.getCreatedAt(), now).toMinutes() >= PAYMENT_EXPIRY_MINUTES) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능 시간이 초과되었습니다.");
        }
    }

    public void validateExpirable(OrderModel order, ZonedDateTime now) {
        if (!order.isPayable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 만료 가능한 주문 상태가 아닙니다.");
        }
        if (Duration.between(order.getCreatedAt(), now).toMinutes() < PAYMENT_EXPIRY_MINUTES) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 만료 가능 시간(15분)이 지나지 않았습니다.");
        }
    }
}
