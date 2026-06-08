package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<OrderEntity> findAllByOrderByIdDesc(Pageable pageable);

    List<OrderEntity> findByStatusOrderByIdDesc(OrderStatus status, Pageable pageable);

    /**
     * 행을 잠그고 조회 (비관적 락, SELECT ... FOR UPDATE) — 결제 결과 반영(markPaid/markFailed) 전용.
     * 동시 reconcile/결제 콜백이 같은 주문을 확정하려 할 때 선행 트랜잭션 커밋까지 대기시켜
     * "정확히 한 번"만 상태 전이·보상이 일어나도록 직렬화한다(재고·쿠폰 이중 원복 방지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);
}
