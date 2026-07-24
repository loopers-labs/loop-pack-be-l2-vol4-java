package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** 월간 TOP 100 랭킹 MV (읽기 전용). @Immutable로 flush 시 UPDATE가 나가지 않도록 DB 레벨까지 막는다. */
@Immutable
@Entity
@Table(name = "mv_product_rank_monthly")
public class MvProductRankMonthly extends MvProductRank {
}
