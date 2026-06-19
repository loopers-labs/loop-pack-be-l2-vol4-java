package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductLikeStatRepository {
    ProductLikeStat save(ProductLikeStat stat);

    /**
     * 이벤트 핸들러에서 사용. 비관적 락 없이 dirty checking 으로 갱신.
     * @Version 으로 동시 increment 충돌은 OptimisticLockingFailureException 으로 잡힘.
     */
    Optional<ProductLikeStat> find(Long productId);

    List<ProductLikeStat> findAllByProductIdIn(List<Long> productIds);

    /** 백필 / 재계산 용 bulk insert */
    void saveAll(List<ProductLikeStat> stats);

    long count();
}
