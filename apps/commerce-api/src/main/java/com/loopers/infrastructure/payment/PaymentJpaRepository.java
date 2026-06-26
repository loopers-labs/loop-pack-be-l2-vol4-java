package com.loopers.infrastructure.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<PaymentModel> findByStatusAndRequestedAtLessThanEqual(PaymentStatus status, ZonedDateTime threshold);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PaymentModel p SET p.status = :status, p.reason = :reason "
        + "WHERE p.id = :paymentId AND p.status IN ("
        + "com.loopers.domain.payment.PaymentStatus.PENDING, com.loopers.domain.payment.PaymentStatus.STUCK)")
    int confirmIfUnresolved(
        @Param("paymentId") Long paymentId,
        @Param("status") PaymentStatus status,
        @Param("reason") String reason
    );
}
