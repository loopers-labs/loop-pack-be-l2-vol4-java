package com.loopers.domain.ranking.batch;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * publish 직전 staging 스냅샷을 검증한다. rank가 1부터 연속인지, productId 중복이 없는지 확인해
 * 검증 실패 시 예외로 Job을 실패시킨다(§2.5 — 반쯤 쓴 데이터가 MV에 노출되지 않도록).
 */
@Component
public class RankingStagingSnapshotValidator {

    public void validateOrThrow(List<RankingStagingRankRow> rows) {
        validateRankIsConsecutive(rows);
        validateNoDuplicateProduct(rows);
    }

    private void validateRankIsConsecutive(List<RankingStagingRankRow> rows) {
        List<RankingStagingRankRow> sorted = rows.stream()
            .sorted(Comparator.comparingInt(RankingStagingRankRow::rank))
            .toList();
        for (int i = 0; i < sorted.size(); i++) {
            int expectedRank = i + 1;
            int actualRank = sorted.get(i).rank();
            if (actualRank != expectedRank) {
                throw new IllegalStateException(
                    "rank가 1부터 연속하지 않습니다: expected=" + expectedRank + ", actual=" + actualRank
                );
            }
        }
    }

    private void validateNoDuplicateProduct(List<RankingStagingRankRow> rows) {
        Set<Long> seenProductIds = new HashSet<>();
        for (RankingStagingRankRow row : rows) {
            if (!seenProductIds.add(row.productId())) {
                throw new IllegalStateException("같은 productId가 중복 랭킹되어 있습니다: productId=" + row.productId());
            }
        }
    }
}
