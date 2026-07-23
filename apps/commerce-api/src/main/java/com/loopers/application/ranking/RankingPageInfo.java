package com.loopers.application.ranking;

import java.util.List;

/**
 * 랭킹 페이지 조회 결과. 항목 목록 + 페이지네이션 메타(전체 건수/현재 페이지/크기)를 담는다.
 *
 * @param items      현재 페이지의 랭킹 항목(내림차순)
 * @param totalCount 해당 일자 랭킹에 오른 전체 상품 수(ZCARD)
 * @param page       1부터 시작하는 현재 페이지
 * @param size       페이지 크기
 */
public record RankingPageInfo(
        List<RankedProductInfo> items,
        long totalCount,
        int page,
        int size
) {
}
