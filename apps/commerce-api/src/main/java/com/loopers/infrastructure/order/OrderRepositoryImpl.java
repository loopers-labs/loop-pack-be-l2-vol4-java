package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
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
    public OrderModel save(OrderModel order) {
        if (order.getId() != null) {
            OrderEntity entity = orderJpaRepository.findById(order.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + order.getId() + "] 주문을 찾을 수 없습니다."));
            entity.updateStatus(order.getStatus());
            return orderJpaRepository.save(entity).toDomain();
        }
        return orderJpaRepository.save(OrderEntity.from(order)).toDomain();
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return orderJpaRepository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public List<OrderModel> findAllByUserId(Long userId) {
        return orderJpaRepository.findAllByUserId(userId).stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    public List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime start, ZonedDateTime end) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetween(userId, start, end).stream()
            .map(OrderEntity::toDomain).toList();
    }

    @Override
    public Page<OrderModel> findAll(Pageable pageable) {
        return orderJpaRepository.findAll(pageable).map(OrderEntity::toDomain);
    }
}
