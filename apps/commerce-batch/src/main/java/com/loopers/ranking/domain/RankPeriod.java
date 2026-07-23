package com.loopers.ranking.domain;

import java.util.Arrays;

/**
 * 랭킹 집계 기간. 배치 Job 파라미터(period)로 전달받아 적재 대상 MV를 결정한다.
 */
public enum RankPeriod {
    WEEKLY {
        @Override
        public ProductRankModel createRank(Long productId, int rank, double score) {
            return new WeeklyProductRankModel(productId, rank, score);
        }

        @Override
        public String targetEntityName() {
            return "WeeklyProductRankModel";
        }
    },
    MONTHLY {
        @Override
        public ProductRankModel createRank(Long productId, int rank, double score) {
            return new MonthlyProductRankModel(productId, rank, score);
        }

        @Override
        public String targetEntityName() {
            return "MonthlyProductRankModel";
        }
    };

    public abstract ProductRankModel createRank(Long productId, int rank, double score);

    /** 적재 전 비우기(clear)에 쓸 대상 엔티티 이름(JPQL 삭제용). */
    public abstract String targetEntityName();

    public static RankPeriod from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("period는 필수입니다. (weekly|monthly)");
        }
        return Arrays.stream(values())
            .filter(p -> p.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 period입니다: " + value));
    }
}
