package com.loopers.batch.job.ranking.dto;

import java.time.LocalDate;

// Writer 입력 DTO. 점수까지 계산된 MV 적재 대상 한 행이다.
// ranking 은 이 단계에서 부여하지 않고(0 으로 insert) 이후 assignRankStep 에서 일괄 부여한다.
public record ProductRankRow(LocalDate aggregateDate, long productId, long score) {
}
