package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    List<OrderModel> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
        Long userId, ZonedDateTime fromInclusive, ZonedDateTime toExclusive);
}
