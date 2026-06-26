package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PG 결제 결과를 우리 상태에 확정(반영)하는 공유 컴포넌트.
 *
 * <p>콜백 수신과 정산(폴링) 양쪽이 이 한 메서드로 수렴한다. 별도 빈이라 프록시를 거쳐
 * {@code @Transactional} 이 정상 적용된다(자기호출 우회). 멱등성은 도메인 전이 메서드가
 * 종결 상태를 no-op 처리해 중복 콜백·정산 재실행을 흡수한다.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentResultApplier {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * transactionKey 에 해당하는 결제를 주어진 결과로 확정한다.
     * 성공 전이가 실제로 일어난 경우에만 주문을 PAID 로 반영한다.
     *
     * <p>{@code amount} 는 통지된 결제 금액이다. 성공 확정 전, 우리가 보관한 결제 금액과 일치하는지
     * 교차검증해 위변조·금액 불일치 통지로 주문을 잘못 PAID 처리하는 돈 사고를 막는다. 금액이 동반되지
     * 않는 경로({@code amount == null}, 예: 정산 조회)는 검사를 건너뛴다.</p>
     */
    @Transactional
    public void apply(String transactionKey, PaymentStatus status, Long amount, String reason) {
        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제건을 찾을 수 없습니다. (transactionKey: " + transactionKey + ")"));

        if (status == PaymentStatus.SUCCESS && amount != null
                && payment.getAmount().getAmount() != amount.longValue()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 금액이 일치하지 않습니다. (expected: " + payment.getAmount().getAmount()
                            + ", notified: " + amount + ", transactionKey: " + transactionKey + ")");
        }

        boolean transitioned = switch (status) {
            case SUCCESS -> payment.markSuccess();
            case FAILED -> payment.markFailed(reason);
            case PENDING -> false; // 아직 처리중 — 확정할 것이 없다(no-op).
        };

        if (transitioned && payment.isSuccess()) {
            Order order = orderRepository.find(payment.getOrderId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "주문을 찾을 수 없습니다. (orderId: " + payment.getOrderId() + ")"));
            order.pay();
            orderRepository.save(order);
        }
    }
}
