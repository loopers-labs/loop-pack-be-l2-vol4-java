package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByTransactionKey(String transactionKey);

    /**
     * 행을 잠그고 조회 (비관적 락, SELECT ... FOR UPDATE) — 결제 결과 반영(markSuccess/markFailed) 전용.
     * 콜백/Reconcile이 같은 거래를 동시에 확정하려 할 때 선행 트랜잭션 커밋까지 대기시켜
     * "정확히 한 번"만 상태 전이가 일어나도록 직렬화한다(주문 이중 확정 방지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.transactionKey = :transactionKey")
    Optional<PaymentEntity> findByTransactionKeyForUpdate(@Param("transactionKey") String transactionKey);

    List<PaymentEntity> findByOrderId(Long orderId);

    /**
     * reconcile 스캔용 — 오래된(id 작은) PENDING부터. TSID는 시간정렬이라 id ASC = 생성 시각 오름차순.
     * 가장 오래(=가장 위험)된 PENDING을 첫 페이지에서 우선 처리해, 백로그가 100건을 넘어도 옛 건이 굶지 않게 한다.
     */
    List<PaymentEntity> findByStatusOrderByIdAsc(PaymentStatus status, Pageable pageable);
}
