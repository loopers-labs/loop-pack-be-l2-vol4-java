package com.loopers.infrastructure.productrank;

import com.loopers.domain.ranking.ProductRankRepository;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductRankRepositoryImpl implements ProductRankRepository {

    // score가 동점일 때 순위가 조회마다 흔들리지 않도록 product_id 오름차순을 2차 정렬 키로 둔다.
    private static final Sort SCORE_DESC_ID_ASC =
            Sort.by(Sort.Direction.DESC, "score").and(Sort.by(Sort.Direction.ASC, "id.productId"));

    private final ProductRankWeeklyMvJpaRepository weeklyJpaRepository;
    private final ProductRankMonthlyMvJpaRepository monthlyJpaRepository;

    @Override
    public List<RankingItem> findTopN(RankingPeriod period, LocalDate asOfDate, long limit, long offset) {
        validateMvPeriod(period);
        OffsetBasedPageRequest pageable = new OffsetBasedPageRequest(offset, (int) limit, SCORE_DESC_ID_ASC);

        List<RankingItem> items = new ArrayList<>();
        long rank = offset + 1;
        if (period == RankingPeriod.WEEKLY) {
            for (ProductRankWeeklyMvJpaEntity row : weeklyJpaRepository.findById_AsOfDate(asOfDate, pageable)) {
                items.add(new RankingItem(row.getId().getProductId(), row.getScore(), rank++));
            }
        } else {
            for (ProductRankMonthlyMvJpaEntity row : monthlyJpaRepository.findById_AsOfDate(asOfDate, pageable)) {
                items.add(new RankingItem(row.getId().getProductId(), row.getScore(), rank++));
            }
        }
        return items;
    }

    @Override
    public long countByAsOfDate(RankingPeriod period, LocalDate asOfDate) {
        validateMvPeriod(period);
        return period == RankingPeriod.WEEKLY
                ? weeklyJpaRepository.countById_AsOfDate(asOfDate)
                : monthlyJpaRepository.countById_AsOfDate(asOfDate);
    }

    private void validateMvPeriod(RankingPeriod period) {
        if (period == RankingPeriod.DAILY) {
            throw new IllegalArgumentException("DAILY 기간은 RDB MV 조회 대상이 아닙니다.");
        }
    }
}
