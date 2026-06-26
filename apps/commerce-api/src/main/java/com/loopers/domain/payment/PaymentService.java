package com.loopers.domain.payment;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 동일 주문의 PENDING은 재사용(멱등), 확정된 건은 CONFLICT. 동시 경합은 UNIQUE(order_id)가 차단한다. */
    @Transactional
    public PaymentModel createPending(Long orderId, Long userId, CardType cardType, Money amount) {
        return paymentRepository.findByOrderId(orderId)
            .map(existing -> {
                if (existing.getStatus() != PaymentStatus.PENDING) {
                    throw new CoreException(ErrorType.CONFLICT, "[orderId = " + orderId + "] 이미 처리된 결제가 있습니다.");
                }
                return existing;
            })
            .orElseGet(() -> {
                try {
                    return paymentRepository.save(new PaymentModel(orderId, userId, cardType, amount));
                } catch (DataIntegrityViolationException e) {
                    // 동시 최초 생성 경합 — UNIQUE(order_id) 충돌을 CONFLICT로 변환
                    throw new CoreException(ErrorType.CONFLICT, "[orderId = " + orderId + "] 이미 처리된 결제가 있습니다.", e);
                }
            });
    }

    @Transactional
    public void assignTransactionKey(Long orderId, String transactionKey) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        payment.assignTransactionKey(transactionKey);
    }

    /** 이미 확정된 건은 no-op — 중복 콜백·폴링에도 이벤트를 재발행하지 않는다(멱등). */
    @Transactional
    public void confirm(String transactionKey, boolean success, String reason) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        if (success) {
            payment.markSuccess();
            eventPublisher.publishEvent(new PaymentCompleted(payment.getOrderId()));
        } else {
            payment.markFailed(reason);
            eventPublisher.publishEvent(new PaymentFailed(payment.getOrderId(), reason));
        }
    }

    /** PG 조회 상태를 확정으로 매핑한다(콜백·복구 공용). 확정 불가 상태(처리 중 등)는 무시. */
    @Transactional
    public void confirmFromGatewayStatus(String transactionKey, String gatewayStatus, String reason) {
        if ("SUCCESS".equals(gatewayStatus)) {
            confirm(transactionKey, true, null);
        } else if ("FAILED".equals(gatewayStatus)) {
            confirm(transactionKey, false, reason != null ? reason : "PG 조회 결과 실패");
        }
    }

    @Transactional(readOnly = true)
    public PaymentModel getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional
    public void failByOrderId(Long orderId, String reason) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.markFailed(reason);
        eventPublisher.publishEvent(new PaymentFailed(orderId, reason));
    }
}
