package com.loopers.application.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.error.PaymentFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 확정 유스케이스 Application Service — 오케스트레이터.
 *
 * <p>프론트가 PG 결제창에서 인증을 마치고 successUrl 로 돌아오면, paymentKey 를 들고
 * 이 confirm 을 호출한다. 흐름:
 *
 * <ol>
 *   <li><strong>검증 (TX, readonly)</strong> — 소유자 / PENDING 상태 / <strong>금액 위변조</strong> 확인.
 *       브라우저를 거쳐 온 amount 는 신뢰할 수 없으므로 DB 주문 금액과 대조한다.</li>
 *   <li><strong>PG 승인 호출 (트랜잭션 밖)</strong> — DB 커넥션을 점유하지 않으므로
 *       PG 응답 지연이 커넥션 풀 고갈로 번지지 않는다. 결제 시도 기록은
 *       {@link PaymentService} 자체 트랜잭션으로 독립 커밋되어 보존된다.</li>
 *   <li><strong>결과 반영 (TX)</strong> — 성공: COMPLETED / 실패: 보상(재고·쿠폰 복구 + FAILED).</li>
 * </ol>
 *
 * <p><strong>TIMEOUT 은 보상하지 않는다</strong>: 타임아웃은 "실패"가 아니라 "결과 미확인"이다.
 * PG 에서는 승인이 완료됐을 수 있으므로, 여기서 재고를 복구해버리면 "돈은 나갔는데 주문은 실패"
 * 사고가 된다. 주문을 PENDING 으로 남겨두고, 만료 스케줄러가 PG 결제 조회로 진실을 확인한 뒤
 * 확정/보상을 결정한다. (실서비스에서는 망취소·대사가 이 역할을 보강한다)
 */
@RequiredArgsConstructor
@Service
public class PaymentApplicationService {

    private final OrderTransactionService orderTransactionService;
    private final PaymentService paymentService;

    public OrderInfo confirmPayment(Long userId, String paymentKey, Long orderId, Long amount) {
        // 1. 소유자 / 상태 / 금액 위변조 검증
        orderTransactionService.validateConfirmable(userId, orderId, amount);

        // 2. PG 승인 호출 — 트랜잭션 밖 (결제 시도 기록은 PaymentService 자체 트랜잭션으로 보존)
        PaymentResult result = paymentService.confirm(paymentKey, orderId, amount);

        // 3-a. 성공 — 주문 확정
        if (result.isSuccess()) {
            return orderTransactionService.completePayment(orderId);
        }

        // 3-b. 타임아웃 — 결과 미확인. 보상 없이 PENDING 유지 (스케줄러가 PG 조회 후 판정)
        if (result.status() == PaymentResult.Status.TIMEOUT) {
            throw new CoreException(ErrorType.INTERNAL_ERROR,
                "결제 결과를 확인하지 못했습니다. 잠시 후 주문 내역에서 결제 상태를 확인해주세요.");
        }

        // 3-c. 실패 — 보상: 재고 복구 + 쿠폰 복구 + 주문 FAILED
        orderTransactionService.failPaymentAndRelease(orderId);
        throw new PaymentFailedException("PAYMENT_FAILED: " + result.failureReasonOrDefault());
    }
}
