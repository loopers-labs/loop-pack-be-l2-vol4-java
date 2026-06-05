package com.loopers.order.application;

import com.loopers.order.domain.Order;
import org.springframework.stereotype.Component;

/**
 * 결제 통합 지점. 현재 범위에서는 stub(껍데기)이며 실제 결제 처리는 하지 않는다.
 * 주문은 PENDING 으로 종료되고, 아래 목표 흐름은 결제 도메인이 추가될 때 채운다.
 *
 * 목표 흐름(결제 도메인 추가 시):
 *  1. 트랜잭션 1: 재고 차감 + 주문 PENDING 저장 후 커밋 (외부 결제 호출 동안 재고 락을 잡지 않기 위해 끊는다).
 *  2. pay(): 외부 결제 시스템 호출 — DB 트랜잭션 바깥.
 *  3. 트랜잭션 2/3: 결제 결과에 따라 별도 트랜잭션에서 전이.
 *     성공 → Order.markPaid(). 실패 → 보상(재고 복구 + Order.markFailed()).
 *
 * TODO(결제 도메인): 결제 성공 직후 상태 전이 전 장애로 "결제는 됐는데 주문은 PENDING"인 주문은
 *  정합성 배치가 PG 와 대조해 PAID/FAILED 로 확정한다. 결제 타임아웃(결과 불명)은 일단 FAILED 로 두고 배치로 재확인한다.
 */
@Component
public class PaymentService {

    public void pay(Order order) {
        // 현재 범위: stub. 상태 전이 없이 주문을 PENDING 으로 둔다.
    }
}
