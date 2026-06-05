package com.loopers.order.application;

import com.loopers.order.domain.OrderNumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 주문번호(yyyyMMdd + 일별 시퀀스)를 채번한다. 예: 20260528-000001.
 *
 * 채번은 주문 트랜잭션과 분리된 짧은 트랜잭션(REQUIRES_NEW)에서 DB 원자적 UPSERT 로 처리한다.
 * 채번 직후 커밋해 행 락을 즉시 해제하므로, 주문 트랜잭션이 재고 차감·저장·결제 동안
 * 채번 락을 붙들지 않는다. 주문이 롤백되면 시퀀스에 갭이 생길 수 있으나,
 * orderNumber 는 표시용 식별자라 갭은 허용된다(결제 멱등성은 별도 키로 보장).
 */
@RequiredArgsConstructor
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NUMBER_FORMAT = "%s-%06d";

    private final OrderNumberSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate() {
        LocalDate today = LocalDate.now();
        long seq = sequenceRepository.nextValue(today);
        return String.format(ORDER_NUMBER_FORMAT, today.format(DATE_FORMAT), seq);
    }
}
