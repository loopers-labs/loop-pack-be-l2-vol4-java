package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentRequestException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentResultApplier paymentResultApplier;
    private final PaymentReserver paymentReserver;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    /**
     * 결제를 요청한다.
     *
     * <p>트랜잭션 경계: 이 메서드는 비-트랜잭션이다. (1) 예약 구간만 {@link PaymentReserver} 의 짧은
     * 트랜잭션에서 주문 행을 비관락(FOR UPDATE)으로 잡고 <b>중복 차단 + PENDING 저장</b>을 원자적으로
     * 수행한 뒤 커밋(락 해제)하고, (2) 느릴 수 있는 PG 호출은 그 락 <b>밖</b>에서 한다.
     * → 같은 주문의 동시 결제(따닥)는 예약 단계에서 직렬화되어 한 건만 PENDING 이 되고 나머지는 CONFLICT 다
     * (PG 호출은 1회). PG 요청이 실패/지연되면 "모름" 으로 보고 PENDING 을 유지한 채 응답하며(자동 단정 금지),
     * 결과는 콜백/정산으로 확정한다.</p>
     */
    public PaymentInfo.Requested pay(PaymentCriteria.Pay criteria) {
        // 1) 예약(잠금 구간): 주문 잠금 → 중복 차단 → PENDING 저장 → 커밋(락 해제).
        Payment payment = paymentReserver.reserve(criteria);

        // 2) PG 요청(락/TX 밖). 실패는 in-doubt 한 비트로 갈라 다룬다:
        //    - in-doubt(read 타임아웃/connect 타임아웃/5xx 등): 청구 여부가 "모름" → 함부로 단정하지 않고
        //      PENDING 유지, 결과는 콜백/정산으로 확정한다.
        //    - not-in-doubt(4xx 거절/CB-open/연결 거부): 청구가 일어나지 않음이 확실 → PENDING 으로 숨기면
        //      콜백도 정산도 닿지 못하는 좀비가 되므로 FAILED 로 확정한다(가드가 정정 후 재시도를 허용).
        PaymentGateway.Result result;
        try {
            result = paymentGateway.request(new PaymentGateway.Command(
                    String.valueOf(criteria.userId()),
                    String.valueOf(criteria.orderId()),
                    criteria.cardType(),
                    criteria.cardNo(),
                    payment.getAmount().getAmount(),
                    callbackUrl));
        } catch (PaymentRequestException e) {
            if (e.isInDoubt()) {
                log.warn("PG 결제 요청 지연/불가(in-doubt) — PENDING 유지 후 정산 대상. paymentId={}, orderId={}, cause={}",
                        payment.getId(), criteria.orderId(), e.getMessage());
                return PaymentInfo.Requested.from(payment);
            }
            log.warn("PG 결제 요청 실패(청구 없음 확실) — FAILED 확정. paymentId={}, orderId={}, cause={}",
                    payment.getId(), criteria.orderId(), e.getMessage());
            payment.markFailed(e.getMessage());
            return PaymentInfo.Requested.from(paymentRepository.save(payment));
        }

        // 3) 접수 확인 — transactionKey 보관(자체 커밋). 상태는 여전히 PENDING.
        payment.assignTransactionKey(result.transactionKey());
        payment = paymentRepository.save(payment);
        return PaymentInfo.Requested.from(payment);
    }

    /**
     * 결제 상태를 조회한다(대고객). 우리 DB 기준의 현재 상태를 그대로 반환하며 PG 를 재호출하지 않는다 →
     * PENDING 이면 "처리중" 으로 정직하게 노출된다(불확실을 실패로 단정하지 않음).
     *
     * <p>{@code Payment} 는 {@code userId} 를 보유하지 않으므로 소유권은 주문에서 도출해 검증한다(정산과 동일).</p>
     */
    @Transactional(readOnly = true)
    public PaymentInfo.Detail getPayment(Long paymentId, Long userId) {
        Payment payment = paymentRepository.find(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제건을 찾을 수 없습니다."));
        Order order = orderRepository.find(payment.getOrderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 결제만 조회할 수 있습니다.");
        }
        return PaymentInfo.Detail.from(payment);
    }

    /**
     * PG 콜백을 수신해 결과를 확정한다. 멱등: 이미 종결된 결제는 no-op, 중복 콜백도 흡수된다.
     */
    public void handleCallback(PaymentCriteria.Callback callback) {
        paymentResultApplier.apply(callback.transactionKey(), callback.status(), callback.amount(), callback.reason());
    }

    /**
     * 정산(수동 복구): PENDING 결제를 PG 상태조회로 되물어 확정한다(콜백 유실 대비).
     * PG 조회는 TX 밖에서 수행하고, 확정만 {@link PaymentResultApplier} 의 트랜잭션으로 위임한다.
     */
    public void reconcile(Long paymentId) {
        Payment payment = paymentRepository.find(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제건을 찾을 수 없습니다."));
        if (!payment.isPending() || payment.getTransactionKey() == null) {
            return; // 이미 종결됐거나, 미접수(키 없음)라 조회 대상 아님.
        }

        // PG 조회의 X-USER-ID 는 주문 소유자에서 조달한다(Payment 는 userId 를 보유하지 않음).
        Order order = orderRepository.find(payment.getOrderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        // 정산 조회 응답(Result)은 금액을 동반하지 않으므로 amount=null 로 전달한다(금액 교차검증은 콜백 경로에서 수행).
        paymentGateway.find(payment.getTransactionKey(), String.valueOf(order.getUserId()))
                .ifPresent(result -> paymentResultApplier.apply(
                        payment.getTransactionKey(), result.status(), null, result.reason()));
    }
}
