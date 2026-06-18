package com.loopers.domain.like;

import com.loopers.domain.common.PageResult;

public interface LikeRepository {

    /** 좋아요 행을 멱등하게 추가한다(INSERT IGNORE). 반환값은 영향받은 행 수 — 1 이면 새로 생김, 0 이면 이미 있음. */
    int save(Like like);

    /** 좋아요 행을 제거한다. 반환값은 영향받은 행 수 — 1 이면 실제로 삭제됨, 0 이면 없었음. */
    int delete(Long userId, Long productId);

    boolean exists(Long userId, Long productId);

    long countByProductId(Long productId);

    PageResult<Like> findAllByUserId(Long userId, int page, int size);
}
