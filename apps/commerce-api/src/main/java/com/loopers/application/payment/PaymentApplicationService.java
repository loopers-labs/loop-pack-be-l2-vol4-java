package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentEntity;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.payment.PaymentWaitingRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentApplicationService {

    private final PaymentService paymentService;
    private final PgClient pgClient;
    private final PaymentWaitingRegistry registry;
    private final String callbackUrl;
    private final long callbackTimeoutSeconds;

    public PaymentApplicationService(
        PaymentService paymentService,
        PgClient pgClient,
        PaymentWaitingRegistry registry,
        @Value("${pg.callback-url:http://localhost:8080/api/v1/payments/callback}") String callbackUrl,
        @Value("${pg.callback-timeout-seconds:10}") long callbackTimeoutSeconds
    ) {
        this.paymentService = paymentService;
        this.pgClient = pgClient;
        this.registry = registry;
        this.callbackUrl = callbackUrl;
        this.callbackTimeoutSeconds = callbackTimeoutSeconds;
    }

    public CompletableFuture<PaymentInfo> initiate(String userId, String orderId, CardType cardType, String cardNo) {
        // TX1: 락 + 검증 + PENDING 저장
        PaymentEntity payment = paymentService.prepare(userId, orderId, cardType, cardNo);
        String paymentId = payment.getId();

        // TX 외부: PG 호출
        PgPaymentRequest pgRequest = new PgPaymentRequest(
            String.valueOf(orderId), cardType, cardNo, payment.getAmount(), callbackUrl
        );
        PgTransactionResponse pgResponse;
        try {
            pgResponse = pgClient.requestPayment(pgRequest, userId);
        } catch (Exception e) {
            paymentService.markFailed(paymentId, "PG 요청 실패");
            throw e;
        }

        // TX2: transactionKey 저장 + PG 즉시 확정 반영
        paymentService.applyPgResponse(paymentId, pgResponse);
        String transactionKey = pgResponse.transactionKey();

        // PG가 즉시 확정(SUCCESS/FAILED)이면 콜백 대기 없이 즉시 반환
        if (pgResponse.status() != PgTransactionStatus.PENDING) {
            return CompletableFuture.completedFuture(infoOf(transactionKey));
        }

        // 콜백 대기 future 구성
        // NOTE: TX2 커밋 후 여기까지 오는 사이에 PG 콜백이 먼저 도착하면 해당 콜백은
        // registry에 future가 없어 complete를 건너뛴다. 이 경우 timeout→poll 경로가
        // safety-net / source-of-truth 역할을 한다 (known latency trade-off, follow-up 예정).
        CompletableFuture<PaymentInfo> innerFuture = new CompletableFuture<>();
        registry.register(transactionKey, innerFuture);
        return innerFuture
            .orTimeout(callbackTimeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                registry.pop(transactionKey);
                try {
                    PgTransactionResponse poll = pgClient.getTransaction(transactionKey, userId); // 1차 Poll
                    if (poll.status() != PgTransactionStatus.PENDING) {
                        paymentService.settle(transactionKey, poll.status(), poll.reason()); // TX3
                    }
                } catch (CoreException pollEx) {
                    // 1차 Poll 실패 → 현재 상태(PENDING) 반환, Scheduler 후속
                }
                return infoOf(transactionKey);
            });
    }

    public void processCallback(String transactionKey, PgTransactionStatus status, String reason) {
        paymentService.settle(transactionKey, status, reason); // first-wins 멱등
    }

    public PaymentInfo getPayment(String userId, String paymentId) {
        PaymentEntity payment = paymentService.getOrThrow(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.");
        }
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getTransactionKey() != null) {
            PgTransactionResponse poll = pgClient.getTransaction(payment.getTransactionKey(), userId);
            if (poll.status() != PgTransactionStatus.PENDING) {
                paymentService.settle(payment.getTransactionKey(), poll.status(), poll.reason());
                return infoOf(payment.getTransactionKey());
            }
        }
        return PaymentInfo.from(payment);
    }

    public PaymentInfo getPaymentByTransactionKey(String transactionKey) {
        return infoOf(transactionKey);
    }

    private PaymentInfo infoOf(String transactionKey) {
        return PaymentInfo.from(paymentService.getByTransactionKey(transactionKey));
    }
}
