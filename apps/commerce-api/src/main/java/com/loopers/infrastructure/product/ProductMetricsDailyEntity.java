package com.loopers.infrastructure.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * product_metrics_daily — <b>일자별</b> 상품 지표. 주간/월간 랭킹 집계의 원천이다(week10).
 *
 * <p><b>왜 product_metrics 에 날짜를 넣지 않고 테이블을 나눴나</b>: {@code product_metrics} 는 상품 리스팅
 * read model 이라 PK 가 {@code product_id} 단독이고 product 와 1:1 이다. 여기에 날짜를 PK 로 추가하면 상품당
 * N 행이 되어 리스팅이 {@code GROUP BY + SUM} 을 타야 하고, week5 에서 만든 좋아요순 복합 인덱스
 * ({@code like_count DESC})와 키셋 페이지네이션 커서가 모두 무효가 된다(정렬 키가 행에 확정돼 있지 않으므로).
 * 리스팅(상품당 1행·현재값)과 기간 랭킹(상품×날짜 N행·구간 합산)은 요구 형태가 상반되므로 읽기 모델을
 * 용도별로 분리한다 — {@code ranking_daily_snapshot} 을 따로 둔 것과 같은 판단이다.
 *
 * <p><b>누적이 아니라 델타를 담는다.</b> {@code product_metrics} 는 전체 총합을 {@code +=} 로 유지하지만
 * 이 테이블은 <b>그날 발생한 양</b>만 기록한다. 그래야 임의 구간을 {@code SUM} 으로 합산할 수 있다.
 * {@code like_delta} 는 좋아요 취소가 있어 <b>음수가 될 수 있다</b>(signed).
 *
 * <p><b>sales_amount 를 함께 두는 이유</b>: 랭킹 스코어가 {@code 0.6 × log10(1+매출)} 이라 수량만으로는
 * 계산할 수 없다. 원자료를 남겨야 {@code RankingScorePolicy} 를 일간·주간·월간에 동일하게 재사용할 수 있다.
 *
 * <p><b>metric_date 는 이벤트 발생시각(KST) 기준</b>이다. 소비 시각으로 잡으면 컨슈머 지연이 자정을 넘길 때
 * 날짜 버킷이 밀린다(랭킹 ZSET 의 일자 계산과 동일한 규약).
 *
 * <p><b>쓰기는 commerce-streamer, 읽기는 commerce-batch</b>. 둘 다 commerce-api 의 JPA 엔티티를 공유하지
 * 않으므로(앱 경계) 각각 {@code JdbcTemplate} 으로 접근한다. 이 엔티티는 <b>스키마 정의 전용</b>이며
 * 상태 변경 메서드를 두지 않는다 — {@code product_metrics} / {@code ranking_daily_snapshot} 과 같은 방식이다.
 *
 * <p><b>왜 JPA 엔티티로 두나</b>: 테스트의 {@code DatabaseCleanUp} 이 JPA 엔티티만 순회해 TRUNCATE 하므로,
 * 비엔티티 테이블은 테스트 간 정리가 되지 않는다.
 *
 * <p><b>PK (metric_date, product_id)</b> 로 같은 날 같은 상품이 한 행에만 쌓인다. streamer 는
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 로 누적하므로 행 선생성이 필요 없다.
 */
@Entity
@Table(name = "product_metrics_daily")
@IdClass(ProductMetricsDailyEntity.Pk.class)
public class ProductMetricsDailyEntity {

    @Id
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 그날의 조회 수(항상 0 이상). */
    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    /** 그날의 좋아요 증감 합. 취소가 더 많으면 <b>음수</b>가 된다. */
    @Column(name = "like_delta", nullable = false)
    private Long likeDelta;

    /** 그날 결제 완료된 판매 수량. */
    @Column(name = "sales_count", nullable = false)
    private Long salesCount;

    /** 그날 결제 완료된 매출액(단가×수량 합). 원자료 — 정책이 바뀌어도 재계산할 수 있게 남긴다. */
    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    /**
     * 그날 주문 스코어의 합({@code Σ 0.6 × log10(1+건별매출)}).
     *
     * <p><b>왜 매출액과 별도로 저장하나</b>: {@code log} 는 합과 교환되지 않는다
     * ({@code Σ log(xᵢ) ≠ log(Σ xᵢ)}). 배치가 {@code SUM(sales_amount)} 에 {@code log10} 을 적용하면
     * 실시간 일간 랭킹(주문 <b>건별</b>로 log)과 산식이 달라져 일간·주간 점수가 서로 다른 체계가 된다.
     * 이벤트를 볼 수 있는 시점(streamer)에 건별 스코어를 미리 더해 두면 기간 집계는 단순 {@code SUM} 으로
     * 끝나고, 어느 기간이든 동일한 스코어 정의를 유지한다.
     */
    @Column(name = "order_score", nullable = false)
    private Double orderScore;

    /** 감사(audit) — 쓰기 경로(streamer JdbcTemplate)가 직접 now() 로 설정한다. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricsDailyEntity() {}

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public Long getLikeDelta() {
        return likeDelta;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public Long getSalesAmount() {
        return salesAmount;
    }

    public Double getOrderScore() {
        return orderScore;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 복합 PK. JPA 규약상 no-arg 생성자 + equals/hashCode 가 필요해 record 로 만들 수 없다. */
    public static class Pk implements Serializable {
        private LocalDate metricDate;
        private Long productId;

        protected Pk() {}

        public Pk(LocalDate metricDate, Long productId) {
            this.metricDate = metricDate;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(metricDate, pk.metricDate) && Objects.equals(productId, pk.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metricDate, productId);
        }
    }
}
