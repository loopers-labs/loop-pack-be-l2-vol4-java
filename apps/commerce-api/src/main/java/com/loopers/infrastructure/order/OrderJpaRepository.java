package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrderModel> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT o FROM OrderModel o WHERE o.memberId = :memberId AND o.createdAt >= :startAt AND o.createdAt <= :endAt")
    List<OrderModel> findAllByMemberIdAndCreatedAtBetween(
        @Param("memberId") Long memberId,
        @Param("startAt") ZonedDateTime startAt,
        @Param("endAt") ZonedDateTime endAt
    );
}
