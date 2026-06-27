package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgApiResponse;
import com.loopers.infrastructure.pg.PgFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentPollingScheduler {

    private static final int MAX_POLLING_COUNT = 5;

    private final PaymentService paymentService;
    private final PgFeignClient pgFeignClient;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    public void pollPendingPayments() {
        List<Payment> payments = paymentService.findAllPendingOrInProgress();
        for (Payment payment : payments) {
            try {
                processPayment(payment);
            } catch (Exception e) {
                log.warn("결제 폴링 처리 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

    private void processPayment(Payment payment) {
        if (payment.getStatus() == PaymentStatus.CREATED) {
            try {
                String pgOrderId = String.format("%06d", payment.getOrderId());
                PgApiResponse.PaymentStatusWithOrderId pgResponse = pgFeignClient.getPaymentStatusByOrderId(
                    String.valueOf(payment.getUserId()),
                    pgOrderId
                );

                Optional<PgApiResponse.Payment> completed = pgResponse.transactions().stream()
                    .filter(t -> "SUCCESS".equals(t.status()) || "FAILED".equals(t.status()))
                    .findFirst();

                if (completed.isPresent()) {
                    PgApiResponse.Payment pgTx = completed.get();
                    Payment updated = paymentService.inProgress(payment, pgTx.transactionKey());
                    PaymentStatus finalStatus = PaymentStatus.valueOf(pgTx.status());
                    paymentService.complete(updated.getTransactionKey(), finalStatus, pgTx.reason());
                    if (finalStatus == PaymentStatus.SUCCESS) {
                        orderService.confirm(updated.getOrderId());
                    }
                    return;
                }

                boolean pgHasTransaction = !pgResponse.transactions().isEmpty();
                if (!pgHasTransaction) {
                    Payment recorded = paymentService.recordPolling(payment);
                    if (recorded.getPollingCount() >= MAX_POLLING_COUNT) {
                        paymentService.exhaustPolling(recorded);
                        log.info("결제 포기 처리: paymentId={}, pollingCount={}", recorded.getId(), recorded.getPollingCount());
                    }
                }
            } catch (Exception e) {
                log.warn("PG 주문 상태 조회 실패: orderId={}", payment.getOrderId(), e);
            }
            return;
        }

        try {
            PgApiResponse.PaymentStatus pgStatus = pgFeignClient.getPaymentStatus(
                String.valueOf(payment.getUserId()),
                payment.getTransactionKey()
            );

            PaymentStatus finalStatus = PaymentStatus.valueOf(pgStatus.status());
            if (finalStatus == PaymentStatus.SUCCESS || finalStatus == PaymentStatus.FAILED) {
                paymentService.complete(payment.getTransactionKey(), finalStatus, pgStatus.reason());
                if (finalStatus == PaymentStatus.SUCCESS) {
                    orderService.confirm(payment.getOrderId());
                }
                return;
            }

            Payment recorded = paymentService.recordPolling(payment);
            if (recorded.getPollingCount() >= MAX_POLLING_COUNT) {
                paymentService.exhaustPolling(recorded);
                log.info("결제 포기 처리: paymentId={}, pollingCount={}", recorded.getId(), recorded.getPollingCount());
            }
        } catch (Exception e) {
            log.warn("PG 상태 조회 실패: transactionKey={}", payment.getTransactionKey(), e);
        }
    }
}