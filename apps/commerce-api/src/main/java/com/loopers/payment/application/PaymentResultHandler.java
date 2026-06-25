package com.loopers.payment.application;

import com.loopers.order.application.OrderPaymentService;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 결과 확정의 공유 진입점. 콜백과 정합성 보정(리컨실러)이 모두 이곳을 호출해, 확정·보상 로직이 갈라지지 않게 한다.
 * 전이는 PENDING 일 때만(멱등 가드), 확정과 보상은 단일 트랜잭션으로 묶는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentResultHandler {

    private static final String ABSENT_TRANSACTION_REASON = "PG 거래 없음(주문 기준 보정) — 결제 미접수로 정리";

    private final PaymentRepository paymentRepository;
    private final OrderPaymentService orderPaymentService;

    @Transactional
    public void handle(PaymentCommand.Confirm command) {
        Optional<Payment> found = paymentRepository.findByTransactionKey(command.transactionKey());
        if (found.isEmpty()) {
            // 키 저장 전 콜백 도착 또는 미존재 — 무시하고 정합성 보정(키 기준 보정)에 회수를 맡긴다.
            log.warn("결제 결과 무시: 알 수 없는 transactionKey={}", command.transactionKey());
            return;
        }
        Payment payment = found.get();
        if (payment.isTerminal()) {
            return; // 멱등: 콜백·보정이 동시에 와도 1회만 반영
        }
        payment.verifyCallback(command.orderNumber(), command.amount());

        if (command.status() == PaymentStatus.SUCCESS) {
            payment.markSuccess();
            orderPaymentService.markPaid(payment.getOrderNumber());
            log.info("결제 SUCCESS 확정 orderNumber={} transactionKey={}", payment.getOrderNumber(), payment.getTransactionKey());
        } else if (command.status() == PaymentStatus.FAILED) {
            payment.markFailed(command.reason());
            orderPaymentService.compensate(payment.getOrderNumber());
            log.info("결제 FAILED 확정+보상 orderNumber={} transactionKey={} reason={}",
                    payment.getOrderNumber(), payment.getTransactionKey(), command.reason());
        }
        // status 가 terminal 이 아니면(아직 PENDING) 아무 것도 하지 않는다.
    }

    /**
     * 주문 기준 보정의 G1 정리: PG 에 거래가 없어(돈이 움직이지 않아) FAILED 로 확정하고 주문을 보상한다.
     * 보상 로직이 갈라지지 않게 콜백·키 기준 보정과 같은 {@link OrderPaymentService#compensate} 를 탄다.
     */
    @Transactional
    public void handleAbsentTransaction(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "정리할 결제를 찾을 수 없습니다."));
        if (payment.isTerminal()) {
            return; // 멱등
        }
        payment.markFailed(ABSENT_TRANSACTION_REASON);
        orderPaymentService.compensate(payment.getOrderNumber());
        log.info("결제 무거래 정리(FAILED)+보상 orderNumber={} paymentId={}", payment.getOrderNumber(), paymentId);
    }
}
