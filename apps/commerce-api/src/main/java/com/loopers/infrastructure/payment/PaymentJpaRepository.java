package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    List<PaymentModel> findAllByStatusAndCreatedAtBefore(PaymentStatus status, ZonedDateTime threshold);

    @Query("SELECT p FROM PaymentModel p WHERE p.orderId = :orderId " +
            "AND p.status IN (com.loopers.domain.payment.PaymentStatus.PENDING, com.loopers.domain.payment.PaymentStatus.PAID) " +
            "ORDER BY p.id DESC")
    List<PaymentModel> findActiveByOrderId(@Param("orderId") Long orderId);

    // 동시성: status='PENDING' 일 때만 PAID 로 전이한다. @Modifying 은 영속성 컨텍스트를 우회하므로
    // (clearAutomatically=true) 후처리는 반드시 반환값(affected rows) 확인 후 재조회로 진행한다.
    // 벌크 UPDATE 는 @PreUpdate 가 발동하지 않으므로 updatedAt 을 명시적으로 갱신한다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentModel p SET p.status = com.loopers.domain.payment.PaymentStatus.PAID, " +
            "p.transactionKey = :key, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status = com.loopers.domain.payment.PaymentStatus.PENDING")
    int transitionToPaid(@Param("id") Long id, @Param("key") String key, @Param("now") ZonedDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentModel p SET p.status = com.loopers.domain.payment.PaymentStatus.FAILED, " +
            "p.failureReason = :reason, p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status = com.loopers.domain.payment.PaymentStatus.PENDING")
    int transitionToFailed(@Param("id") Long id, @Param("reason") String reason, @Param("now") ZonedDateTime now);

    // 무결성 불일치/상한 초과 격리도 조건부 UPDATE 로 처리한다(설계 §8) — managed entity dirty update 는
    // status 가드가 없어 동시 전이(PAID/FAILED)와 race 시 terminal 을 덮어쓸 수 있으므로 PENDING 만 전이시킨다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentModel p SET p.status = com.loopers.domain.payment.PaymentStatus.UNKNOWN, " +
            "p.updatedAt = :now " +
            "WHERE p.id = :id AND p.status = com.loopers.domain.payment.PaymentStatus.PENDING")
    int transitionToUnknown(@Param("id") Long id, @Param("now") ZonedDateTime now);
}
