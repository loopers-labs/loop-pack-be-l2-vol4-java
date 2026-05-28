package com.loopers.infrastructure.order;

import com.loopers.domain.order.model.OrderItemSnapshot;
import com.loopers.domain.order.repository.OrderItemSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderItemSnapshotRepositoryImpl implements OrderItemSnapshotRepository {

    private final OrderItemSnapshotJpaRepository orderItemSnapshotJpaRepository;

    @Override
    public List<OrderItemSnapshot> saveAll(List<OrderItemSnapshot> snapshots) {
        return orderItemSnapshotJpaRepository.saveAll(snapshots);
    }
}
