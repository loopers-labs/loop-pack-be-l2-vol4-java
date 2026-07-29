package com.loopers.batch.job.ranking.step;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;

/** 집계 결과에 기간 키를 붙인다. score 는 DB 가 이미 계산했다. */
@RequiredArgsConstructor
public class RankingRowProcessor implements ItemProcessor<RankingRow, RankingMvRow> {

    private final String periodKey;

    @Override
    public RankingMvRow process(RankingRow row) {
        return new RankingMvRow(periodKey, row.productId(), row.score(),
                row.viewCount(), row.likeCount(), row.salesCount());
    }
}
