package com.loopers.application.product;

import java.util.List;

/** 키셋 커서 페이지 결과. nextCursor 가 null 이면 마지막 페이지. */
public record ProductCursorPage(List<ProductInfo> items, String nextCursor) {
}
