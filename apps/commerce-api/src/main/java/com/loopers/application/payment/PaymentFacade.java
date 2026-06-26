package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.PgRequest;
import com.loopers.infrastructure.pg.PgResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgClient pgClient;

    @Value("${pg.client.callback-url}")
    private String callbackUrl;

    public PaymentModel requestPayment(Long memberId, Long orderId, CardType cardType, String cardNo, Long amount) {
        PaymentModel payment = paymentService.create(orderId, cardType, cardNo, amount);

        try {
            PgRequest.CreateTransaction request = new PgRequest.CreateTransaction(
                String.format("%012d", orderId),
                cardType.name(),
                cardNo,
                amount,
                callbackUrl
            );
            PgResponse.TransactionResponse response = pgClient.createTransaction(String.valueOf(memberId), request);
            paymentService.assignTransactionKey(orderId, response.transactionKey());
        } catch (Exception e) {
            if (isTimeout(e)) {
                log.warn("[orderId={}] PG 타임아웃 발생 - 배치 복구 대기", orderId);
                return paymentService.getById(payment.getId());
            }
            log.error("[orderId={}] PG 호출 실패 - 결제 실패 처리", orderId, e);
            paymentService.failByOrderId(orderId, "PG 처리 실패");
            orderService.cancelBySystem(orderId);
            return paymentService.getById(payment.getId());
        }
        return paymentService.getById(payment.getId());
    }

    public void handleCallback(String transactionKey, String status, String reason) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        Long orderId = payment.getOrderId();

        if ("SUCCESS".equals(status)) {
            paymentService.success(transactionKey);
            orderService.confirmBySystem(orderId);
        } else {
            paymentService.failByTransactionKey(transactionKey, reason);
            orderService.cancelBySystem(orderId);
        }
    }

    private boolean isTimeout(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
