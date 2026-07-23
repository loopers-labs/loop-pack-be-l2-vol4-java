package com.loopers.infrastructure.ranking;

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
 * ranking_daily_snapshot — 일간 랭킹의 영속 스냅샷. ZSET 은 TTL 2일이라 그 이후엔 사라지므로,
 * 확정된 하루치 상위 N 을 배치가 DB 로 내려 과거 조회를 가능하게 한다.
 *
 * <p><b>쓰기는 commerce-batch, 읽기는 commerce-api</b>. 앱 경계라 코드를 공유하지 않으므로 batch 는
 * 이 엔티티 없이 {@code JdbcTemplate} 로 직접 INSERT 한다(product_metrics 를 streamer 가 쓰는 방식과 동일).
 * 이 엔티티는 <b>스키마 정의 + 읽기 전용</b>이며 상태 변경 메서드를 두지 않는다.
 *
 * <p><b>왜 JPA 엔티티인가</b>: event_handled 처럼 import.sql 로만 만들 수도 있었지만, 테스트의
 * {@code DatabaseCleanUp} 이 <b>JPA 엔티티만 순회해 TRUNCATE</b> 하므로 비엔티티 테이블은 테스트 간 정리가 되지 않는다.
 *
 * <p><b>PK (ranking_date, product_id)</b>: 같은 날짜를 두 번 적재해도 중복 행이 생기지 않는다. 배치는
 * 해당 일자를 지우고 다시 넣으므로(delete-then-insert) 재실행이 안전하다.
 *
 * <p>{@code rank} 는 MySQL 8 예약어(RANK() 윈도우 함수)라 컬럼명은 {@code rank_no} 를 쓴다.
 */
@Entity
@Table(name = "ranking_daily_snapshot")
@IdClass(RankingSnapshotEntity.Pk.class)
public class RankingSnapshotEntity {

    @Id
    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 스냅샷 시점에 확정된 1-based 순위. 읽을 때 재계산하지 않고 그대로 쓴다. */
    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "score", nullable = false)
    private Double score;

    /** 적재 시각 — 쓰기 경로(batch JdbcTemplate)가 직접 now() 로 설정한다. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected RankingSnapshotEntity() {}

    public LocalDate getRankingDate() {
        return rankingDate;
    }

    public Long getProductId() {
        return productId;
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
        private LocalDate rankingDate;
        private Long productId;

        public Pk() {}

        public Pk(LocalDate rankingDate, Long productId) {
            this.rankingDate = rankingDate;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk other)) {
                return false;
            }
            return Objects.equals(rankingDate, other.rankingDate) && Objects.equals(productId, other.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rankingDate, productId);
        }
    }
}
