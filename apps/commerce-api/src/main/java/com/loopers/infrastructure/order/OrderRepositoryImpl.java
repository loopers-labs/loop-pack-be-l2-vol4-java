package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태만 복사 → dirty checking으로 UPDATE.
     *   (BaseEntity의 id가 final이라 도메인을 그대로 새 엔티티로 만들면 INSERT로 오인되므로 이 경로가 필요하다.)
     */
    @Override
    public OrderModel save(OrderModel order) {
        if (order.getId() == null) {
            OrderEntity saved = orderJpaRepository.save(OrderEntityMapper.toEntity(order));
            return OrderEntityMapper.toDomain(saved);
        }
        OrderEntity entity = orderJpaRepository.findById(order.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + order.getId() + "] 주문을 찾을 수 없습니다."));
        entity.applyState(order.getStatus(), order.getTotalAmount().getAmount(), order.getFailureReason(), order.getPaidAt());
        return OrderEntityMapper.toDomain(orderJpaRepository.save(entity));
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return orderJpaRepository.findById(id).map(OrderEntityMapper::toDomain);
    }
}
