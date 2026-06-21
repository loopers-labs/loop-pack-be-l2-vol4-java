package com.loopers.infrastructure.ordering.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = order.isNew()
            ? OrderJpaEntity.from(order)
            : orderJpaRepository.findById(order.getId()).orElseGet(() -> OrderJpaEntity.from(order));
        entity.apply(order);
        return orderJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Order> find(Long orderId) {
        return orderJpaRepository.findById(orderId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findForUpdate(Long orderId) {
        return orderJpaRepository.findWithLockById(orderId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByIdAndUserId(Long orderId, String userId) {
        return orderJpaRepository.findByIdAndUserId(orderId, userId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findByUserIdAndCreatedAtBetween(String userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                userId,
                startAt,
                endAt
            )
            .stream()
            .map(OrderJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Order> findAllForAdmin(int page, int size) {
        return orderJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
            .stream()
            .map(OrderJpaEntity::toDomain)
            .toList();
    }

    @Override
    public long countAll() {
        return orderJpaRepository.count();
    }
}
