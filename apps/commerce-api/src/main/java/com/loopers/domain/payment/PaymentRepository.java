package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    Optional<PaymentModel> findByOrderId(Long orderId);

    /** 복구 대상 조회: 아직 종료되지 않은(PENDING/PROCESSING) 결제들 */
    List<PaymentModel> findByStatusIn(List<PaymentStatus> statuses);
}
