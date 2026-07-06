package com.loopers.payment.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByTransactionKey(String transactionKey);

    Optional<Payment> findActiveByOrderNumber(String orderNumber);

    /** PENDING + transactionKey 보유 + 생성 후 N초 경과 — 콜백 유실 회수 대상. */
    List<Payment> findStalePendingWithKey(ZonedDateTime before);

    /** PENDING + transactionKey 없음 + 생성 후 N초 경과 — TX2 전 크래시 회수 대상. */
    List<Payment> findStalePendingWithoutKey(ZonedDateTime before);
}
