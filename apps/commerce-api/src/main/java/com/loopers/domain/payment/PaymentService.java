package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 트랜잭션 책임 Service.
 *
 * <p><strong>스타일 선택 - Vernon 절충 (스타일 2 예외)</strong>:
 * {@link com.loopers.domain.order.OrderService} 와 동일한 사유로 Service 계층을 유지한다.
 *
 * <ul>
 *   <li>외부 PG ({@link PaymentGateway}) 동기 호출이 트랜잭션 내부에서 일어남</li>
 *   <li>REQUESTED → SUCCESS/FAILED 상태 전이가 같은 트랜잭션에서 기록되어야</li>
 *   <li>실패 시에도 결과를 예외가 아닌 {@link PaymentResult} 로 반환 (호출자가 보상 처리)</li>
 * </ul>
 *
 * <p>이 패턴은 Vernon 의 "복잡한 트랜잭션엔 Domain Service 허용" 견해를 따른다.
 */
@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    /**
     * 결제 승인을 수행한다 (인증 → 승인 2단계 중 승인 단계).
     *
     * <ul>
     *   <li>REQUESTED 상태로 Payment 를 먼저 저장 — 결제 시도 자체를 기록</li>
     *   <li>PG 승인 API 호출 (server-to-server)</li>
     *   <li>결과에 따라 SUCCESS / FAILED 로 상태 변경</li>
     *   <li><strong>TIMEOUT 은 REQUESTED 로 남긴다</strong> — 타임아웃은 "실패"가 아니라
     *       "결과 미확인"이다. PG 에서는 승인이 완료됐을 수 있으므로 FAILED 로 단정하면 안 되고,
     *       REQUESTED 잔존 건은 결제 조회/대사의 대상이 된다.</li>
     * </ul>
     *
     * <p>호출자(PaymentApplicationService)는 주문 트랜잭션 밖에서 이 메서드를 호출하므로,
     * 여기서 열리는 트랜잭션은 독립적으로 커밋된다 — 결제 실패 기록도
     * 주문 보상 처리와 무관하게 보존되어 추후 PG 대사의 근거가 된다.
     *
     * <p>알려진 한계: 동일 주문 재시도 시 {@code order_id} UK 위반이 발생한다.
     * 실서비스에서는 멱등키 기반 재시도로 해결하지만 과제 범위에서는 다루지 않는다.
     */
    @Transactional
    public PaymentResult confirm(String paymentKey, Long orderId, Long amount) {
        PaymentModel payment = paymentRepository.save(new PaymentModel(orderId, amount));

        PaymentResult result;
        try {
            result = paymentGateway.confirm(paymentKey, orderId, amount);
        } catch (Exception e) {
            result = PaymentResult.failed("외부 결제 시스템 호출 중 예외 발생: " + e.getMessage());
        }

        if (result.isSuccess()) {
            payment.markSuccess(result.pgTransactionId());
        } else if (result.status() == PaymentResult.Status.TIMEOUT) {
            // 결과 미확인 — REQUESTED 유지. 보상/확정 판단은 조회·대사 이후로 미룬다.
        } else {
            payment.markFailed(result.failureReason());
        }

        return result;
    }
}
