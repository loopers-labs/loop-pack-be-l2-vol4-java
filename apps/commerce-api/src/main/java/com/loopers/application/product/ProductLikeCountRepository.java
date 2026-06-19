package com.loopers.application.product;

import java.util.List;
import java.util.Optional;

public interface ProductLikeCountRepository {
    boolean increase(Long productId, Integer baseLikeCount);

    boolean decrease(Long productId, Integer baseLikeCount);

    Optional<Integer> get(Long productId);

    List<ProductLikeCountSnapshot> getDirtyCounts(int limit);

    void clearDirty(Long productId);
}
