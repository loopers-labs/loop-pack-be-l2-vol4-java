package com.loopers.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 주문번호의 일별 채번 카운터. 도메인 애그리거트가 아니라 채번 메커니즘이므로
 * order_date 를 자연키로 쓰는 단순 카운터 테이블로 둔다(BaseEntity·soft delete 불필요).
 * 증분은 DB 원자적 UPSERT 로 처리하며(인프라 계층), 동시 주문에서의 채번 충돌은 DB 가 직렬화한다.
 */
@Entity
@Table(name = "order_number_sequences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderNumberSequence {

    @Id
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;
}
