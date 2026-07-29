package com.loopers.metrics.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/** {@link ProductMetric} 의 복합키. 필드명이 엔티티의 {@code @Id} 필드와 일치해야 한다. */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricId implements Serializable {

    private Long productId;
    private LocalDate statDate;
}
