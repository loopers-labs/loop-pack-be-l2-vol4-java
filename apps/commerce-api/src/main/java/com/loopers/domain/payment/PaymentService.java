package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    /**
     * 주문에 대한 결제 요청을 수행한다.
     *
     * - REQUESTED 상태로 Payment를 먼저 저장 (결제 시도 자체를 기록)
     * - 외부 PG 호출
     * - 결과에 따라 SUCCESS / FAILED로 상태 변경
     *
     * 호출자(OrderService)가 결과를 보고 보상 로직(주문 취소 + 재고 복구)을 수행한다.
     * 이 메서드는 결제 실패 시 예외를 던지지 않고 PaymentResult로 결과를 전달한다.
     */
    @Transactional
    public PaymentResult process(Long orderId, Long amount) {
        PaymentModel payment = paymentRepository.save(new PaymentModel(orderId, amount));

        PaymentResult result;
        try {
            result = paymentGateway.request(orderId, amount);
        } catch (Exception e) {
            result = PaymentResult.failed("외부 결제 시스템 호출 중 예외 발생: " + e.getMessage());
        }

        if (result.isSuccess()) {
            payment.markSuccess(result.pgTransactionId());
        } else {
            payment.markFailed(result.failureReason());
        }

        return result;
    }
}
