package com.loopers.batch.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 주간/월간 랭킹 Materialized View 의 한 행(상위 N위 중 하나). 배치가 일별 집계를 재집계해 적재하는 조회 전용 파생 데이터다.
 * 주간/월간이 행 모양이 동일해 공통 필드를 여기 모으고, 테이블 매핑만 하위 엔티티(Weekly/Monthly)가 각자 가진다.
 *
 * snapshot_date: rolling 윈도우 기준일. "이 날짜 기준 지난 7일/30일"의 랭킹이라는 뜻 (week10 qna Q5, rolling 선택).
 * window 교체 시 (snapshot_date 로 물리 DELETE 후 재삽입) 하드 삭제되므로, soft-delete 를 가진 BaseEntity 는 상속하지 않는다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProductRankMv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    // rank 는 MySQL 8 예약어(윈도우 함수)라 컬럼명을 rank_no 로 둔다.
    @Column(name = "rank_no", nullable = false)
    private int rank;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private double score;

    protected ProductRankMv(LocalDate snapshotDate, int rank, Long productId, double score) {
        this.snapshotDate = snapshotDate;
        this.rank = rank;
        this.productId = productId;
        this.score = score;
    }
}
