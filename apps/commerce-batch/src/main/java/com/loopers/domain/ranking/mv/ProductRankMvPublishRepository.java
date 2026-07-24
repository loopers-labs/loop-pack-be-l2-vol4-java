package com.loopers.domain.ranking.mv;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 검증된 staging 스냅샷을 실제 MV 테이블(mv_product_rank_weekly/monthly)에 게시하는 포트.
 * 같은 period의 기존 행을 delete하고 새 행을 insert하는 트랜잭션 교체만 지원한다(§2.5).
 */
public interface ProductRankMvPublishRepository {

    void replaceWeeklyPeriod(String periodKey, List<ProductRankMvRow> rows, ZonedDateTime publishedAt);

    void replaceMonthlyPeriod(String periodKey, List<ProductRankMvRow> rows, ZonedDateTime publishedAt);
}
