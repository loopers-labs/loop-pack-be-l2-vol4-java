package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * 주문 PK는 앱 생성 TSID라 신규에도 id가 채워져 있으므로, id 유무 대신 <b>실제 영속 여부</b>로 분기한다.
     * - 미존재(findById 비어 있음): 매퍼로 엔티티를 만들어 INSERT(OrderEntity.isNew()로 persist 유도).
     * - 기존: managed 엔티티를 로드해 가변 상태만 복사 → dirty checking으로 UPDATE.
     */
    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.findById(order.getId())
                .map(entity -> {
                    entity.applyState(order.getStatus(), order.getTotalAmount().getAmount(),
                            order.getFailureReason(), order.getPaidAt());
                    return OrderEntityMapper.toDomain(orderJpaRepository.save(entity));
                })
                .orElseGet(() -> OrderEntityMapper.toDomain(
                        orderJpaRepository.save(OrderEntityMapper.toEntity(order))));
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return orderJpaRepository.findById(id).map(OrderEntityMapper::toDomain);
    }

    @Override
    public Optional<OrderModel> findForUpdate(Long id) {
        return orderJpaRepository.findByIdForUpdate(id).map(OrderEntityMapper::toDomain);
    }

    @Override
    public List<OrderModel> findByUserId(Long userId, int page, int size) {
        return orderJpaRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, size)).stream()
                .map(OrderEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<OrderModel> findAll(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        List<OrderEntity> entities = (status == null)
                ? orderJpaRepository.findAllByOrderByIdDesc(pageable)
                : orderJpaRepository.findByStatusOrderByIdDesc(status, pageable);
        return entities.stream().map(OrderEntityMapper::toDomain).toList();
    }
}
