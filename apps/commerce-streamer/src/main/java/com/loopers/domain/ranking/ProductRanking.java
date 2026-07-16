package com.loopers.domain.ranking;

public interface ProductRanking {

    /** 오늘 일자 랭킹 키에 상품 점수를 delta 만큼 누적한다. */
    void addScore(Long productId, double delta);
}