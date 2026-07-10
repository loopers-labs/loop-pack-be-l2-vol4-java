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
        // 날짜는 ISO 문자열(yyyy-MM-dd)로 바인딩해 TZ 변환이 끼어들 자리를 없앤다.
        // 증분의 행 X-lock 이 동시 채번을 직렬화하므로, 같은 트랜잭션의 조회가 자기 증분값을 읽는다.
        String date = orderDate.toString();
        orderNumberSequenceJpaRepository.incrementOrCreate(date);
        return orderNumberSequenceJpaRepository.currentValue(date);
    }
}
