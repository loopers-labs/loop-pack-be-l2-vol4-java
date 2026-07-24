package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MonthlyProductRankModel;
import com.loopers.domain.ranking.ProductRankId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MonthlyProductRankJpaRepository extends JpaRepository<MonthlyProductRankModel, ProductRankId> {

    /** 배치 재실행 시 해당 기간의 기존 랭킹을 비우고 새로 적재하기 위한 삭제. */
    void deleteByPeriodStart(LocalDate periodStart);
}
