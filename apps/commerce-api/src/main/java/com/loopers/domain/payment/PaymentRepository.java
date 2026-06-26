package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    /**
     * 결제 결과 반영(markSuccess/markFailed) 전용 — 행을 비관적 락으로 잠그고 조회한다.
     * 동시 확정(콜백/Reconcile)을 직렬화해 "정확히 한 번"만 상태 전이가 일어나도록 한다.
     */
    Optional<PaymentModel> findByTransactionKeyForUpdate(String transactionKey);

    /** 주문에 엮인 결제 목록 (멱등 가드 + Reconcile용). */
    List<PaymentModel> findByOrderId(Long orderId);

    /** 상태별 결제 목록 — Reconcile에서 PENDING 결제를 최신순으로 페이지 조회. */
    List<PaymentModel> findByStatus(PaymentStatus status, int page, int size);
}
