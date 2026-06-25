package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 "예약"(잠금 구간)을 담당하는 컴포넌트.
 *
 * <p>같은 주문에 대한 동시 결제 시도(따닥)를 막기 위해, 주문 행을 비관적 쓰기 락(FOR UPDATE)으로 잡고
 * <b>검사(진행중/완료 결제 존재 여부) → PENDING 저장</b>을 원자적으로 수행한다. 검사-삽입이 한 트랜잭션
 * 안에서 직렬화되므로, 응용 레벨의 check-then-act 경쟁(TOCTOU)이 사라진다.</p>
 *
 * <p>락은 이 짧은 트랜잭션 동안만 유지되고 커밋과 함께 해제된다 → 느릴 수 있는 PG 호출은 이 락 <b>밖</b>
 * (호출자 {@link PaymentApplicationService#pay}의 비-트랜잭션 구간)에서 수행된다. 즉 외부 호출을 락 안에
 * 품지 않는다. 별도 빈이라 프록시를 거쳐 {@code @Transactional} 이 정상 적용된다(자기호출 우회).</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentReserver {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 주문을 잠그고 중복 결제를 차단한 뒤 PENDING 결제를 저장해 반환한다.
     *
     * <p>차단 기준은 "살아있는 시도"다: 같은 주문에 <b>PENDING(진행중) 또는 SUCCESS(완료)</b> 결제가
     * 있으면 {@link ErrorType#CONFLICT}. FAILED(종결)만 있으면 정당한 재시도로 보고 허용한다.</p>
     */
    @Transactional
    public Payment reserve(PaymentCriteria.Pay criteria) {
        // 1) 주문 잠금(FOR UPDATE) — 같은 주문의 동시 예약을 직렬화하는 조정점.
        Order order = orderRepository.findForUpdate(criteria.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(criteria.userId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 주문만 결제할 수 있습니다.");
        }

        // 2) 중복 가드(락 안이라 race-safe): 살아있는 시도(PENDING/SUCCESS)가 있으면 차단.
        boolean blocked = paymentRepository.findAllByOrderId(criteria.orderId())
                .stream().anyMatch(p -> p.isSuccess() || p.isPending());
        if (blocked) {
            throw new CoreException(ErrorType.CONFLICT, "이미 진행 중이거나 완료된 결제가 있습니다.");
        }

        // 3) PENDING 저장 — 여기까지가 잠금 구간. 커밋과 함께 락 해제 후, 호출자가 PG 를 호출한다.
        long amount = order.getTotalAmount().getAmount();
        return paymentRepository.save(
                Payment.create(criteria.orderId(), criteria.cardType(), criteria.cardNo(), Money.of(amount)));
    }
}
