package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 기간 랭킹 Materialized View 의 공통 스키마(week10). 주간/월간이 컬럼 구조가 같아 여기에 모으고,
 * 서브클래스가 테이블명만 다르게 매핑한다.
 *
 * <p><b>왜 MV(조회 전용 테이블)인가</b>: 주간/월간 랭킹을 요청 시점에 계산하면 매번
 * {@code product_metrics_daily} 7~30일치를 {@code GROUP BY} 하고 전역 정렬해야 한다. 상품 수가 늘면
 * 조회 지연이 그대로 커지고, 같은 결과를 사용자마다 다시 계산하는 낭비가 생긴다. 배치가 하루 한 번
 * 미리 확정해 두면 조회는 {@code (period_start, rank_no)} 인덱스 레인지 스캔 한 번으로 끝난다.
 *
 * <p><b>period_start 가 기간의 식별자</b>다. 주간은 그 주 <b>월요일</b>(ISO-8601), 월간은 그 달 1일.
 * {@code period_end} 는 조회 응답에 구간을 그대로 노출하기 위해 함께 저장한다(계산으로도 얻을 수 있지만,
 * 주 정의가 바뀌어도 적재 당시 구간이 보존되는 편이 해석에 안전하다).
 *
 * <p><b>PK (period_start, product_id)</b> — 같은 기간을 두 번 적재해도 중복 행이 생기지 않는다.
 * 배치는 해당 기간을 지우고 다시 넣으므로(delete-then-insert) 재실행이 안전하다.
 *
 * <p>{@code rank} 는 MySQL 8 예약어(RANK() 윈도우 함수)라 컬럼명은 {@code rank_no} 를 쓴다
 * ({@code ranking_daily_snapshot} 과 동일).
 *
 * <p><b>쓰기는 commerce-batch, 읽기는 commerce-api</b>. batch 는 앱 경계라 이 엔티티 없이
 * {@code JdbcTemplate} 으로 직접 INSERT 한다. 이 엔티티는 스키마 정의 + 읽기 전용이다.
 */
@MappedSuperclass
@IdClass(ProductRankMvEntity.Pk.class)
public abstract class ProductRankMvEntity {

    /** 기간 시작일. 주간=그 주 월요일, 월간=그 달 1일. */
    @Id
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 기간 종료일(포함). 주간=일요일, 월간=말일. */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 배치가 확정한 1-based 순위. 읽을 때 재계산하지 않고 그대로 쓴다. */
    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    /** 구간 합산 스코어(RankingScorePolicy 동일 가중치). */
    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected ProductRankMvEntity() {}

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public Long getProductId() {
        return productId;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Integer getRankNo() {
        return rankNo;
    }

    public Double getScore() {
        return score;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    /** 복합 PK. JPA 규약상 no-arg 생성자 + equals/hashCode 가 필요해 record 로 만들 수 없다. */
    public static class Pk implements Serializable {
        private LocalDate periodStart;
        private Long productId;

        protected Pk() {}

        public Pk(LocalDate periodStart, Long productId) {
            this.periodStart = periodStart;
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
            return Objects.equals(periodStart, pk.periodStart) && Objects.equals(productId, pk.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(periodStart, productId);
        }
    }
}
