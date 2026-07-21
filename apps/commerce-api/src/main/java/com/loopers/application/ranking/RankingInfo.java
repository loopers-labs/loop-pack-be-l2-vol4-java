package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

/**
 * 랭킹 항목 — 절대 순위(1-indexed) + 상품 정보.
 *
 * @param rank    전체 랭킹 기준 순위(페이지 오프셋 반영)
 * @param product 상품 기본 정보(목록 화면용 조립)
 */
public record RankingInfo(int rank, ProductInfo product) {
}
