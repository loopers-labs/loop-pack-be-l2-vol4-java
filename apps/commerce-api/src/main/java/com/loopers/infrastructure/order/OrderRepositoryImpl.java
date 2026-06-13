package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        if (order.getId() == null) {
            return orderJpaRepository.save(
                new OrderEntity(order.getUserId(), order.getIssuedCouponId(), order.getOriginalPrice(), order.getDiscountAmount())
            ).toDomain();
        }
        OrderEntity entity = orderJpaRepository.findById(order.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
        entity.updateFrom(order);
        return orderJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public List<Order> findAllByUserIdAndPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt)
            .stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderJpaRepository.findAll(pageable).map(OrderEntity::toDomain);
    }
}
