package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> find(Long id);

    /** 콜백 / 폴링 매칭용 — PG 가 발급한 transactionKey 로 조회. */
    Optional<Payment> findByTransactionKey(String transactionKey);

    /** 같은 주문에 진행 중(REQUESTED/IN_PROGRESS/UNKNOWN) 인 결제 시도가 있는지 멱등 체크. */
    List<Payment> findActiveByOrderId(Long orderId);

    /** 한 주문의 전체 결제 시도 조회. */
    List<Payment> findAllByOrderId(Long orderId);

    /**
     * 폴링 대상 조회 — status IN (IN_PROGRESS, UNKNOWN) AND createdAt < threshold.
     * 폴링 backoff 적용을 위해 호출자가 임계 시각을 전달한다.
     */
    List<Payment> findReconciliationTargets(ZonedDateTime threshold);

    /**
     * 타임아웃 대상 조회 — status IN (IN_PROGRESS, UNKNOWN) AND createdAt < threshold(=now - timeout).
     * 호출자가 강제로 FAILED 전이 + 재고 복구를 트리거한다.
     */
    List<Payment> findTimeoutTargets(ZonedDateTime threshold);
}
