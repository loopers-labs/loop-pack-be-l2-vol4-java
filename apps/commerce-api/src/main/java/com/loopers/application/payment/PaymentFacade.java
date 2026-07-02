package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.*;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    /**
     * 결제 접수. PG 호출 전 PENDING 을 선삽입·커밋(UNIQUE 락)하고,
     * 외부 호출은 DB 트랜잭션 밖에서 수행한다(커넥션 점유 방지). 결과는 콜백/폴링으로 확정.
     */
    public PaymentInfo pay(String loginId, PaymentCommand command) {
        UserModel user = userService.getMyInfo(loginId);
        OrderModel order = orderService.getOrder(command.orderId());

        if (!order.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 결제할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문 상태입니다.");
        }

        long amount = order.getFinalAmount();

        paymentService.createPending(order.getId(), user.getId(), amount, command.cardType(), command.cardNo());

        PgRequestResult result = paymentGateway.requestPayment(
                PgPaymentCommand.of(order.getId(), user.getId(), amount, command.cardType(), command.cardNo())
        );

        switch (result.outcome()) {
            case ACCEPTED -> paymentService.markRequested(order.getId(), result.transactionKey());
            case TIMEOUT -> paymentService.markAttemptedWithoutKey(order.getId());
            case NOT_ATTEMPTED -> {
                // 서킷 Open/소진: PENDING 유지(미시도). 재요청/복구 대상이며 결제 자체는 죽이지 않는다.
            }
        }

        PaymentModel paymentModel = paymentService.getByOrderId(order.getId());
        return PaymentInfo.from(paymentModel);
    }

    /**
     * 콜백/폴링 공용 결과 반영 경로. 결제 상태를 반영하고, 종결이면 주문에 멱등하게 전이한다.
     * 콜백과 폴링이 같은 결과를 동시에 반영해도 주문 confirm/fail 이 이중 실행되지 않도록 가드.
     */
    public void reflect(Long orderId, String transactionKey, PaymentStatus status, Long amount, String reason) {
        paymentService.applyResult(orderId, transactionKey, status, amount, reason);

        OrderModel order = orderService.getOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        if (status == PaymentStatus.SUCCESS) {
            orderService.confirm(orderId);
        } else if (status == PaymentStatus.FAILED) {
            orderService.fail(orderId);
        }
    }
}
