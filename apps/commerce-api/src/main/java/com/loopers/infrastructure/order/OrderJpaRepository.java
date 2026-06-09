package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {
    Optional<OrderModel> findByIdAndDeletedAtIsNull(Long id);
    List<OrderModel> findAllByMemberIdAndDeletedAtIsNull(Long memberId);
    List<OrderModel> findAllByMemberIdAndCreatedAtBetweenAndDeletedAtIsNull(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt);
}
