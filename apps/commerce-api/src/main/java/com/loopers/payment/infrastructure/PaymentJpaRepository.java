package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPgTransactionKey(String pgTransactionKey);

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

    @Query("""
        select p
        from Payment p
        where (
            (p.status = :requestingStatus and p.requestedAt <= :requestingDeadline)
            or (p.status = :pendingStatus and p.requestedAt <= :pendingDeadline)
            or p.status = :unknownStatus
        )
        and (p.nextRecoveryAt is null or p.nextRecoveryAt <= :now)
        order by p.requestedAt asc, p.id asc
        """)
    List<Payment> findRecoverablePayments(
        ZonedDateTime now,
        PaymentStatus requestingStatus,
        ZonedDateTime requestingDeadline,
        PaymentStatus pendingStatus,
        ZonedDateTime pendingDeadline,
        PaymentStatus unknownStatus,
        Pageable pageable
    );
}
