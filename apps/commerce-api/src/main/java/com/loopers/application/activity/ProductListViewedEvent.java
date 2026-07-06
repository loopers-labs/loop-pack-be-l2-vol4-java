package com.loopers.application.activity;

/**
 * 상품 목록 페이지 조회 이벤트 — 탐색 행동 로깅용.
 *
 * <p>주어가 "상품"이 아니라 <strong>"페이지"</strong>다: 목록 요청 1건 = 이벤트 1건.
 * 목록에 실린 상품들의 노출(impression)을 상품별 조회수(view_count)로 집계하면 지표가 오염되므로,
 * 상품별 집계는 상세 조회({@link ProductViewedEvent})만 사용하고 목록은 페이지 단위 행동 로그로만 남긴다.
 * (정렬 선호/페이지 깊이/브랜드 탐색 패턴 분석 용도)
 *
 * @param brandId 브랜드 필터. 전체 목록이면 null.
 * @param sort    정렬 옵션 (latest / price_asc / likes_desc).
 * @param page    0-base 페이지 번호.
 */
public record ProductListViewedEvent(Long brandId, String sort, int page) {
}
