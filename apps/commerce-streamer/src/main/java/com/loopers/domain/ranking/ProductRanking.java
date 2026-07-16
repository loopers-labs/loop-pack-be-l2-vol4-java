package com.loopers.domain.ranking;

import java.time.LocalDate;

public interface ProductRanking {

    /** 오늘 일자 랭킹 키에 상품 점수를 delta 만큼 누적한다. */
    void addScore(Long productId, double delta);

    /** fromDate 랭킹 점수에 weight 를 곱해 toDate 키로 미리 복사한다(콜드 스타트 완화). */
    void carryOver(LocalDate fromDate, LocalDate toDate, double weight);
}