package com.loopers.ranking.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * product_metrics의 집계 카운트로 랭킹 점수를 계산한다.
 * 조회·좋아요는 선형 가중치, 판매는 log10으로 정규화해 한 상품의 대량 판매가 전부를 부수지 않게 한다.
 * (이벤트 단위의 streamer RankingScorePolicy와 달리, 여기선 금액이 없는 누적 카운트를 다룬다)
 * 가중치는 설정(ranking.weight)에서 주입받아 재배포 없이 조절할 수 있다.
 */
@RequiredArgsConstructor
@Component
public class RankingScorePolicy {

    private final RankingWeightProperties weights;

    public double score(long viewCount, long likeCount, long salesCount) {
        // log10(1 + n): sales_count가 0이면 0점이 되고, 대량 판매는 log 스케일로 완만히 반영한다
        return viewCount * weights.view()
            + likeCount * weights.like()
            + Math.log10(1 + (double) salesCount) * weights.order();
    }
}
