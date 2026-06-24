package com.loopers.application.product;

import java.util.List;

/**
 * 상품 목록 키셋 페이지 결과 (application 레벨) — 한 페이지의 항목 + 다음 페이지를 잇는 불투명 커서.
 * {@code nextCursor}가 null이면 마지막 페이지({@code hasNext == false}).
 */
public record ProductListResult(
    List<ProductListItemInfo> items,
    String nextCursor,
    boolean hasNext
) {
}
