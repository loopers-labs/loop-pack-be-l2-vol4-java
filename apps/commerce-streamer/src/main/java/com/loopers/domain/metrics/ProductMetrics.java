package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 상품별 "일별" 집계(좋아요 수·판매량·조회 수 + 정규화된 주문 점수). 이벤트를 원천으로 upsert 로만 갱신되는 파생 데이터다.
 * 주간/월간 배치가 이 일별 행을 기간 단위로 재집계하는 소스(SoT)이므로, (product_id, metric_date) 로 하루 단위를 구분한다.
 *
 * 키 설계: 복합키(@IdClass/@EmbeddedId)는 코드베이스 전례가 없어, 대리키(id) + (product_id, metric_date) 유니크 제약으로 둔다.
 * 리포지토리의 단문 upsert(INSERT ... ON DUPLICATE KEY UPDATE)는 이 유니크 키에 걸려 "그 날 그 상품" 행을 증감한다.
 *
 * order_score: 주문 점수는 로그 정규화(0.7*log10(price*qty+1))가 비선형이라, 카운트를 사후 합산해도 복원되지 않는다.
 * 그래서 정규화를 "집계 시점(일별)"에 적용해 그날의 주문 점수 합으로 저장하고, 배치는 이 값을 단순 SUM 한다 (week9→week10 qna Q6).
 *
 * 이 엔티티는 스키마 정의와 조회 용도로만 쓴다(실제 갱신은 native upsert).
 */
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_metrics_product_date", columnNames = {"product_id", "metric_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate metricDate;

    @Column(nullable = false)
    private Long likeCount;

    @Column(nullable = false)
    private Long saleCount;

    @Column(nullable = false)
    private Long viewCount;

    @Column(nullable = false)
    private Double orderScore;
}
