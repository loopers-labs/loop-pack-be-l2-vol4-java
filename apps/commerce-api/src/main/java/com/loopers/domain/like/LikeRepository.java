package com.loopers.domain.like;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LikeRepository {
    LikeModel save(LikeModel like);

    /** (userId, productId) 단일 행 조회 — 활성/비활성 무관 (UNIQUE 제약, reactivate 패턴용). */
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * 비활성→활성 전이를 원자적으로 수행하고 영향 행 수(0/1)를 반환한다.
     * 1이면 이 호출이 실제로 전이시킨 것 → 호출부가 카운터를 증가한다(동시 reactivate 이중카운트 방지).
     */
    int activate(Long userId, Long productId);

    /** 활성→비활성 전이를 원자적으로 수행하고 영향 행 수를 반환한다(동시 unlike 이중차감 방지). */
    int deactivate(Long userId, Long productId);

    /** 특정 상품의 활성 좋아요 전체 — Product 비활성 시 cascade 전파용 (01 §7.5). */
    List<LikeModel> findActiveByProductId(Long productId);

    /** 특정 사용자의 활성 좋아요를 좋아요 시점 최신순으로 페이지 조회 (UC-07). */
    List<LikeModel> findActiveByUserId(Long userId, int page, int size);

    /** 주어진 상품들 중 사용자가 활성 좋아요한 productId 집합 — 목록 좋아요 여부 batch 조회 (UC-03, N+1 회피). */
    Set<Long> findLikedProductIds(Long userId, Collection<Long> productIds);
}
