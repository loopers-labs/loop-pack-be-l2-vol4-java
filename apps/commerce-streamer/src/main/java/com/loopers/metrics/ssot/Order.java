package com.loopers.metrics.ssot;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매량(PAID 기준) 재계산의 SSOT — commerce-api orders 테이블의 read-only 뷰(status 만 필요).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(name = "status", nullable = false)
    private String status;
}
