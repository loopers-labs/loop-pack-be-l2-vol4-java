package com.loopers.domain.productrank;

/** 읽기모델 정렬 결과 1건: 정렬 키(like_count)와 product_id. 커서·표시 모두 이 값을 쓴다. */
public record RankedProduct(long productId, long likeCount) {
}
