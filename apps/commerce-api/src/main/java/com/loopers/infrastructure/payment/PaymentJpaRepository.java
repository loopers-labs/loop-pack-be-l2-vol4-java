package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentPendingReason;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByIdAndUserLoginId(Long id, String userLoginId);
    Optional<PaymentJpaEntity> findByOrderIdAndUserLoginId(Long orderId, String userLoginId);
    Optional<PaymentJpaEntity> findByOrderId(Long orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PaymentJpaEntity p
        set p.status = :status,
            p.pendingReason = :pendingReason,
            p.transactionKey = :transactionKey,
            p.reason = :reason
        where p.id = :id
          and p.status = :pendingStatus
        """)
    int completeIfPending(
        @Param("id") Long id,
        @Param("status") PaymentStatus status,
        @Param("pendingReason") PaymentPendingReason pendingReason,
        @Param("transactionKey") String transactionKey,
        @Param("reason") String reason,
        @Param("pendingStatus") PaymentStatus pendingStatus
    );
}
