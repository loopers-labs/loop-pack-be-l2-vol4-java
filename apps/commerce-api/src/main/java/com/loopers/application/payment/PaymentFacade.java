package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgFeignClient;
import com.loopers.infrastructure.pg.PgPaymentRequest;
import com.loopers.infrastructure.pg.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PgFeignClient pgFeignClient;
    private final String pgCallbackUrl;

    public PaymentFacade(
        OrderService orderService,
        PaymentService paymentService,
        PgFeignClient pgFeignClient,
        @Value("${pg.callback-url}") String pgCallbackUrl
    ) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.pgFeignClient = pgFeignClient;
        this.pgCallbackUrl = pgCallbackUrl;
    }

    public PaymentInfo.Create requestPayment(PaymentCommand.Request command) {
        Order order = orderService.getOrder(command.orderId());

        if (!order.getUserId().equals(command.userId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 결제할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "결제 가능한 상태의 주문이 아닙니다.");
        }
        if (paymentService.hasSuccessPayment(command.orderId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 완료된 주문입니다.");
        }

        Payment payment = paymentService.createPayment(
            command.userId(), command.orderId(), command.cardType(), command.cardNo(), order.getTotalPrice().longValue()
        );

        PgPaymentResponse pgResponse;
        try {
            pgResponse = pgFeignClient.requestPayment(
                String.valueOf(command.userId()),
                new PgPaymentRequest(
                    String.format("%06d", command.orderId()),
                    command.cardType().name(),
                    command.cardNo(),
                    order.getTotalPrice().longValue(),
                    pgCallbackUrl
                )
            );
        } catch (FeignException e) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 서버에 일시적인 오류가 발생했습니다.");
        }

        paymentService.inProgress(payment, pgResponse.transactionKey());
        return new PaymentInfo.Create(pgResponse.transactionKey());
    }

    public void receiveCallback(PaymentCommand.Callback command) {
        Payment payment = paymentService.getByTransactionKey(command.transactionKey());

        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        paymentService.complete(command.transactionKey(), command.status(), command.reason());

        if (command.status() == PaymentStatus.SUCCESS) {
            orderService.confirm(payment.getOrderId());
        }
    }
}
