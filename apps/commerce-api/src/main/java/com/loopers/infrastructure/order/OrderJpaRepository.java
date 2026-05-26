package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id")
    Optional<OrderModel> findWithItemsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM OrderModel o WHERE o.userId = :userId AND o.orderedAt BETWEEN :startAt AND :endAt")
    List<OrderModel> findByUserIdAndOrderedAtBetween(
        @Param("userId") Long userId,
        @Param("startAt") ZonedDateTime startAt,
        @Param("endAt") ZonedDateTime endAt
    );

    @EntityGraph(attributePaths = {"items"})
    Page<OrderModel> findAll(Pageable pageable);
}
