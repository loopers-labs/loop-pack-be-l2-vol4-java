package com.loopers.domain.ordering.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> find(Long orderId);

    default Optional<Order> findForUpdate(Long orderId) {
        return find(orderId);
    }

    Optional<Order> findByIdAndUserId(Long orderId, String userId);

    List<Order> findByUserIdAndCreatedAtBetween(String userId, ZonedDateTime startAt, ZonedDateTime endAt);

    List<Order> findAllForAdmin(int page, int size);

    long countAll();
}
