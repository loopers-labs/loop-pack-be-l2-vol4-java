package com.loopers.payment.application;

import com.loopers.common.domain.Money;
import com.loopers.payment.domain.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 통합 지점. 현재 범위에서는 게이트웨이 호출만 위임하는 stub 이며 상태 전이는 하지 않는다.
 *
 * 목표 흐름(결제 도메인 본구현 시):
 *  1. 트랜잭션 1: 재고 차감 + 주문 PENDING 저장 후 커밋 (외부 결제 호출 동안 재고 락을 잡지 않기 위해 끊는다).
 *  2. pay(): PaymentGateway 로 외부 결제 호출 — DB 트랜잭션 바깥.
 *  3. 트랜잭션 2/3: 결제 결과에 따라 별도 트랜잭션에서 전이.
 *     성공 → Order.markPaid(). 실패 → 보상(재고 복구 + Order.markFailed()).
 *
 * TODO(결제 도메인): 결제 성공 직후 상태 전이 전 장애로 "결제는 됐는데 주문은 PENDING"인 주문은
 *  정합성 배치가 PG 와 대조해 PAID/FAILED 로 확정한다. 결제 타임아웃(결과 불명)은 일단 FAILED 로 두고 배치로 재확인한다.
 */
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentGateway paymentGateway;

    public void pay(Long orderId, Money amount) {
        paymentGateway.requestPayment(orderId, amount);
    }
}
