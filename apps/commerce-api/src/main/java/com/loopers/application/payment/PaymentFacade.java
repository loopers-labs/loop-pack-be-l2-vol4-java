package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgClientWrapper;
import com.loopers.infrastructure.pg.PgRequest;
import com.loopers.infrastructure.pg.PgResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketTimeoutException;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgClientWrapper pgClient;

    // self-invocation via proxy — requestPayment()의 실패 경로에서 @Transactional 적용을 위해 필요
    @Lazy
    @Autowired
    private PaymentFacade self;

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
            if (isTimeout(e) || e instanceof CallNotPermittedException) {
                log.warn("[orderId={}] PG 타임아웃/서킷 오픈 - 배치 복구 대기", orderId);
            } else {
                log.error("[orderId={}] PG 호출 실패 - 결제 실패 처리", orderId, e);
                self.recoverAsFailure(orderId, "PG 처리 실패");
            }
        }
        return paymentService.getById(payment.getId());
    }

    @Transactional
    public void recoverAsSuccess(Long orderId, String transactionKey) {
        paymentService.successByOrderId(orderId, transactionKey);
        orderService.confirmBySystem(orderId);
    }

    public void recoverAsFailure(Long orderId, String reason) {
        paymentService.failByOrderId(orderId, reason);
        OrderModel order = orderService.getById(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            orderService.cancelBySystem(orderId);
        }
    }

    @Transactional
    public void handleCallback(String transactionKey, String status, String reason) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);

        if ("SUCCESS".equals(status)) {
            if (payment.getStatus() != PaymentStatus.PENDING) return;
            paymentService.success(transactionKey);
            orderService.confirmBySystem(payment.getOrderId());
        } else if ("FAILED".equals(status)) {
            if (payment.getStatus() != PaymentStatus.PENDING) return;
            paymentService.failByTransactionKey(transactionKey, reason);
            OrderModel order = orderService.getById(payment.getOrderId());
            if (order.getStatus() != OrderStatus.CANCELLED) {
                orderService.cancelBySystem(payment.getOrderId());
            }
        } else {
            log.warn("[transactionKey={}] 알 수 없는 콜백 상태 수신: {}", transactionKey, status);
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
