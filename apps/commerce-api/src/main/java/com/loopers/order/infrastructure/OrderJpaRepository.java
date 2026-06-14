package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long orderId);

    @Query(
        value = "select o.id from Order o",
        countQuery = "select count(o) from Order o"
    )
    Page<Long> findIds(Pageable pageable);

    @Query(
        value = """
            select o.id
            from Order o
            where o.orderer.userId = :userId
              and (:startAt is null or o.createdAt >= :startAt)
              and (:endBefore is null or o.createdAt < :endBefore)
            """,
        countQuery = """
            select count(o)
            from Order o
            where o.orderer.userId = :userId
              and (:startAt is null or o.createdAt >= :startAt)
              and (:endBefore is null or o.createdAt < :endBefore)
            """
    )
    Page<Long> findIdsByUserIdAndPeriod(
        @Param("userId") Long userId,
        @Param("startAt") ZonedDateTime startAt,
        @Param("endBefore") ZonedDateTime endBefore,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "items")
    @Query("select distinct o from Order o where o.id in :orderIds")
    List<Order> findAllWithItemsByIdIn(@Param("orderIds") Collection<Long> orderIds);
}
