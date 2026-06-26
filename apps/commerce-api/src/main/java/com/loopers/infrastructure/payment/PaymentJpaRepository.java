package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionKey(String transactionKey);

    List<Payment> findAllByOrderId(Long orderId);

    /**
     * 진행 중 결제(멱등 체크용). REQUESTED / IN_PROGRESS / UNKNOWN 모두 "아직 끝나지 않은" 상태로 본다.
     */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.orderId = :orderId
          AND p.status IN :statuses
    """)
    List<Payment> findActiveByOrderId(@Param("orderId") Long orderId,
                                     @Param("statuses") List<PaymentStatus> statuses);

    /**
     * 폴링 / 타임아웃 대상 조회 — status IN (IN_PROGRESS, UNKNOWN) AND createdAt < threshold.
     * 호출자가 임계 시각과 status 셋을 결정해 어떤 backoff / timeout 정책에도 재사용 가능하게 한다.
     */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status IN :statuses
          AND p.createdAt < :threshold
    """)
    List<Payment> findByStatusInAndCreatedAtBefore(@Param("statuses") List<PaymentStatus> statuses,
                                                  @Param("threshold") ZonedDateTime threshold);
}
