package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @Query("SELECT o FROM OrderModel o WHERE o.userId = :userId AND o.createdAt >= :from AND o.createdAt < :to")
    List<OrderModel> findAllByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to
    );

    @Query("SELECT DISTINCT o FROM OrderModel o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderModel> findByIdWithItems(@Param("id") Long id);
}
