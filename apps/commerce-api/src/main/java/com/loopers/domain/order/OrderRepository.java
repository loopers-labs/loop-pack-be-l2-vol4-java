package com.loopers.domain.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    OrderItemModel saveItem(OrderItemModel item);
    Optional<OrderModel> findById(Long id);
    Optional<OrderModel> findByIdForUpdate(Long id);
    List<OrderModel> findAllByMemberIdAndDateRange(Long memberId, LocalDate startAt, LocalDate endAt);
    List<OrderModel> findAllByDateRange(LocalDate startAt, LocalDate endAt);
    List<OrderItemModel> findItemsByOrderId(Long orderId);
    List<OrderItemModel> findItemsByOrderIdIn(List<Long> orderIds);
}
