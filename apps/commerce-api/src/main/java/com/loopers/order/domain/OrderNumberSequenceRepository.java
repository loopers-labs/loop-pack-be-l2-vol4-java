package com.loopers.order.domain;

import java.time.LocalDate;

public interface OrderNumberSequenceRepository {

    /**
     * 해당 날짜의 채번을 원자적으로 1 증가시키고 증가된 값을 반환한다.
     * 증분은 DB UPSERT 로 처리되어 동시 주문에서도 충돌 없이 고유한 순번을 보장한다.
     */
    long nextValue(LocalDate orderDate);
}
