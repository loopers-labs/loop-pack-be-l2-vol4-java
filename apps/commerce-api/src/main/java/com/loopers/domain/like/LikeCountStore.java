package com.loopers.domain.like;

/**
 * 좋아요 수 증감을 흡수하는 포트.
 * <p>핫 쓰기를 RDB 핫 로우/인덱스 갱신에서 분리하기 위해 증감분(delta)을 별도 저장소(Redis)에 누적한다.
 * 누적된 증감분은 배치(commerce-batch)가 주기적으로 product.like_count에 반영한다.
 * 읽기(목록/상세)는 여전히 product.like_count 컬럼을 사용하므로 배치 주기만큼 지연 반영된다.</p>
 */
public interface LikeCountStore {
    void increment(Long productId);
    void decrement(Long productId);
}
