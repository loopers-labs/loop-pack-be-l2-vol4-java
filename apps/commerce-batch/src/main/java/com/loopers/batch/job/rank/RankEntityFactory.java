package com.loopers.batch.job.rank;

import java.time.LocalDate;

/**
 * 순위·점수·기간을 받아 MV 엔티티를 만드는 팩토리 — 주간/월간 엔티티 생성자 참조를 그대로 넘길 수 있다.
 */
@FunctionalInterface
public interface RankEntityFactory<T> {
    T create(int rank, Long productId, double score, LocalDate periodStart, LocalDate periodEnd);
}
