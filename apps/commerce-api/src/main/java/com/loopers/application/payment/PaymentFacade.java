package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentTempStorage paymentTempStorage;

    public PaymentStatus getPaymentStatus(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentModel::getStatus)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
    }

    public Long processPayment(Long orderId, PaymentMethod method, BigDecimal amount) {
        // 1. READY 상태로 저장 (단일 데이터 변경 작업, save API 자체 트랜잭션으로 바로 커밋)
        PaymentModel payment = new PaymentModel(orderId, method, amount);
        payment = paymentRepository.save(payment);
        Long paymentId = payment.getId();

        // 2. Redis에 TTL 10초 설정 (추상화된 TempStorage 사용)
        try {
            paymentTempStorage.setRetryCount(paymentId, 0, Duration.ofSeconds(10));
        } catch (Exception e) {
            log.error("Failed to write to Redis for payment retry trace: {}", paymentId, e);
        }

        // 3. 외부 PG API 호출 (트랜잭션 바깥이므로 DB 락 및 커넥션 점유 없음)
        try {
            PaymentGatewayResult result = paymentGateway.requestPayment(orderId, amount, method);
            
            // 4. 성공 시 상태 APPROVED로 변경 (단일 데이터 변경 작업)
            PaymentModel targetPayment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
            targetPayment.approve(result.transactionId(), result.approvedAt());
            paymentRepository.save(targetPayment);
            
            // 5. 성공 후 Redis 키 제거
            paymentTempStorage.deleteRetryKey(paymentId);
        } catch (Exception e) {
            log.warn("Payment PG request timeout or failed, keeping READY status for payment id: {}", paymentId, e);
        }

        return paymentId;
    }

    public void retryOrCompensatePayment(Long paymentId) {
        // Step 3/4에서 상세 로직 구현 예정
    }
}
