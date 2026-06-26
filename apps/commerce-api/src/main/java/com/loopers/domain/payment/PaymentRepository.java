package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findById(Long id);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    // 요청 측 멱등성("따닥 클릭"): 해당 주문의 활성(PENDING/PAID) 결제 1건을 조회한다.
    // pay() 진입 시 이미 있으면 PG 를 새로 호출하지 않고 멱등 반환한다.
    Optional<PaymentModel> findActiveByOrderId(Long orderId);

    // 동시성 처리: 조건부 UPDATE (status='PENDING' 인 행만 전이, check-then-act 갭 제거).
    // affected rows 로 승자를 판별한다 — 1이면 호출자가 후처리를 정확히 1회 실행, 0이면 스킵한다.
    int transitionToPaid(Long id, String transactionKey);

    int transitionToFailed(Long id, String reason);

    // 격리도 조건부 UPDATE (PENDING 만 전이). 동시 전이와 race 시 terminal 을 덮어쓰지 않도록 보장.
    int transitionToUnknown(Long id);

    // 폴링 대상: grace period(threshold) 이전에 생성된 PENDING 결제 목록.
    List<PaymentModel> findPendingOlderThan(ZonedDateTime threshold);
}
