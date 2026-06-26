package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> find(Long id);

    Optional<Payment> findByTransactionKey(String transactionKey);

    /**
     * 한 주문에 대한 모든 결제 시도. 최신 시도 도출·정합성 복구(정산)에 사용한다.
     */
    List<Payment> findAllByOrderId(Long orderId);

    /**
     * 콜백 미수신 대비 정산 대상: 아직 PENDING 으로 남아있는 결제들.
     */
    List<Payment> findAllByStatus(PaymentStatus status);
}
