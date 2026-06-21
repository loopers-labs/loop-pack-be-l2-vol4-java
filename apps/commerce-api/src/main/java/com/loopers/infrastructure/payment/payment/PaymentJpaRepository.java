package com.loopers.infrastructure.payment.payment;

import com.loopers.domain.payment.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByOrderId(Long orderId);

    List<PaymentJpaEntity> findByOrderIdIn(Collection<Long> orderIds);

    List<PaymentJpaEntity> findByStatus(PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from PaymentJpaEntity p
        where p.status = :status
        order by p.createdAt asc
        """)
    List<PaymentJpaEntity> findByStatusForUpdate(@Param("status") PaymentStatus status, Pageable pageable);
}
