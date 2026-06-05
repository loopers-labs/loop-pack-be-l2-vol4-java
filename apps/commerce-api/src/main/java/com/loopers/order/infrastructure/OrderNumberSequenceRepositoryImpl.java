package com.loopers.order.infrastructure;

import com.loopers.order.domain.OrderNumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class OrderNumberSequenceRepositoryImpl implements OrderNumberSequenceRepository {

    private final OrderNumberSequenceJpaRepository orderNumberSequenceJpaRepository;

    @Override
    public long nextValue(LocalDate orderDate) {
        // UPSERT 가 행을 X-lock 한 상태에서 같은 트랜잭션으로 값을 읽으므로,
        // 동시 주문에서도 다른 트랜잭션이 끼어들지 못해 우리 채번 값을 정확히 반환한다.
        orderNumberSequenceJpaRepository.incrementOrCreate(orderDate);
        return orderNumberSequenceJpaRepository.currentValue(orderDate);
    }
}
