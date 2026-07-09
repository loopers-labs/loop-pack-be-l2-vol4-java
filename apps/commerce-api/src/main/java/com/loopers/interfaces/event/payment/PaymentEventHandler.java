package com.loopers.interfaces.event.payment;

import com.loopers.application.order.OrderService;
import com.loopers.application.payment.PaymentService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.infrastructure.pg.PgClientWrapper;
import com.loopers.infrastructure.pg.PgRequest;
import com.loopers.infrastructure.pg.PgResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.SocketTimeoutException;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentEventHandler {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgClientWrapper pgClient;

    @Value("${pg.client.callback-url}")
    private String callbackUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentRequestedEvent event) {
        try {
            PgRequest.CreateTransaction request = new PgRequest.CreateTransaction(
                String.format("%012d", event.orderId()),
                event.cardType().name(),
                event.cardNo(),
                event.amount(),
                callbackUrl
            );
            PgResponse.TransactionResponse response = pgClient.createTransaction(
                String.valueOf(event.memberId()), request
            );
            paymentService.assignTransactionKey(event.orderId(), response.transactionKey());
        } catch (Exception e) {
            if (isTimeout(e) || e instanceof CallNotPermittedException) {
                log.warn("[orderId={}] PG 타임아웃/서킷 오픈 - 배치 복구 대기", event.orderId());
            } else {
                log.error("[orderId={}] PG 호출 실패 - 결제 실패 처리", event.orderId(), e);
                recoverAsFailure(event.orderId());
            }
        }
    }

    private void recoverAsFailure(Long orderId) {
        paymentService.failByOrderId(orderId, "PG 처리 실패");
        OrderModel order = orderService.getById(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED) {
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
