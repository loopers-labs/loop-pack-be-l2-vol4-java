package com.loopers.batch.job.ranking.period.step;

import com.loopers.batch.job.ranking.period.PeriodMetricsSum;
import com.loopers.batch.job.ranking.period.PeriodScore;
import com.loopers.batch.job.ranking.period.PeriodScorePolicy;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 구간 합산 지표 → 스코어 변환(Chunk 의 Processor 단계).
 *
 * <p><b>스코어가 0 이하면 {@code null} 을 돌려 걸러낸다.</b> Spring Batch 는 Processor 가 {@code null} 을
 * 반환한 아이템을 Writer 로 넘기지 않는다(filter). 좋아요가 취소만 쌓여 음수가 된 상품이나 활동이 상쇄돼
 * 0 이 된 상품은 랭킹에 올릴 의미가 없고, staging 행만 늘려 Step2 정렬 비용을 키운다.
 */
@Component
public class PeriodScoreProcessor implements ItemProcessor<PeriodMetricsSum, PeriodScore> {

    @Override
    public PeriodScore process(PeriodMetricsSum item) {
        double score = PeriodScorePolicy.score(item.viewSum(), item.likeSum(), item.orderScoreSum());
        if (score <= 0.0) {
            return null; // filtered — Writer 로 넘어가지 않는다
        }
        return new PeriodScore(item.productId(), score);
    }
}
