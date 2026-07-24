package com.loopers.batch.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주간/월간 집계 배치의 중간 산출물(상품별 기간 점수). Step1(전체 상품 점수 계산)이 적재하고 Step2(정렬 top100)가 읽는 다리다.
 * 한 번의 배치 실행용 scratch 공간이라 매 실행 시작(clearStep)에 전부 비운다.
 * 주간·월간 잡은 스케줄 시각이 달라 동시 실행하지 않는다는 전제로 period 구분 없이 단순하게 둔다(동시 실행이 필요해지면 period 추가).
 * idx(score): Step2 의 ORDER BY score DESC 조회를 위한 인덱스.
 */
@Entity
@Table(name = "product_score_staging", indexes = @Index(name = "idx_staging_score", columnList = "score"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductScoreStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private double score;

    public ProductScoreStaging(Long productId, double score) {
        this.productId = productId;
        this.score = score;
    }
}
