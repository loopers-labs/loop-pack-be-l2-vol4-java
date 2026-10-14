package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByOrderId(Long orderId);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    /**
     * 복구 대상 — TIMEOUT_PENDING 전부 + 일정 시간 이전에 REQUESTED 로 전이됐는데 콜백을 못 받은 건.
     */
    @Query("""
        SELECT p FROM PaymentModel p
         WHERE (p.status = com.loopers.domain.payment.PaymentStatus.TIMEOUT_PENDING)
            OR (p.status = com.loopers.domain.payment.PaymentStatus.REQUESTED AND p.updatedAt < :before)
         ORDER BY p.updatedAt ASC
        """)
    List<PaymentModel> findRecoverable(@Param("before") ZonedDateTime before, Pageable limit);

    List<PaymentModel> findAllByStatus(PaymentStatus status);
}
