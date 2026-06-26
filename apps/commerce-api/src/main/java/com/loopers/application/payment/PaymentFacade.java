package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    /**
     * 결제를 요청한다. 트랜잭션 경계와 외부 호출을 분리한다:
     * (tx) 결제건 생성 → (tx 밖) PG 호출 → (tx) transactionKey 연결.
     * PG HTTP 호출 동안 DB 커넥션을 점유하지 않아 풀 고갈/장애 전파를 막는다.
     */
    public PaymentInfo requestPayment(String loginId, String loginPw, Long orderId, CardType cardType, String cardNo) {
        UserModel user = userService.getUser(loginId, loginPw);
        OrderModel order = orderService.getOrder(orderId);

        if (!order.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 결제할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제 가능한 주문 상태가 아닙니다.");
        }

        // 결제건 생성
        PaymentModel payment = paymentService.create(user.getId(), orderId, cardType, cardNo, order.getFinalPrice());

        // PG 결과 요청
        PaymentGateway.Result result = paymentGateway.requestPayment(
            new PaymentGateway.Command(user.getId(), orderId, cardType, cardNo, order.getFinalPrice())
        );

        // transactionKey 연결
        PaymentModel linked = paymentService.linkTransactionKey(payment.getId(), result.transactionKey());
        return PaymentInfo.from(linked);
    }

    /**
     * PG 결과(콜백)를 반영한다. 결제 상태 전이는 멱등(PaymentService.applyResult)하며,
     * SUCCESS일 때만 주문을 확정한다. OrderService.confirm 도 멱등이라 중복 콜백에 안전하다.
     */
    public void handleCallback(String transactionKey, PaymentStatus status, String reason) {
        PaymentModel payment = paymentService.applyResult(transactionKey, status, reason);
        // SUCCESS일 때만 주문을 확정한다.
        if (status == PaymentStatus.SUCCESS) {
            orderService.confirm(payment.getOrderId());
        }
    }
}
