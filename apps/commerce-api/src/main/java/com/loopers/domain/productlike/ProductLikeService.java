package com.loopers.domain.productlike;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductService productService;

    /**
     * 좋아요 등록 + 비정규화 count 증가를 하나의 트랜잭션으로 처리한다.
     * <p>
     * 이미 좋아요 상태면 무시하고 false를 반환한다(멱등). 새로 insert되면 count를 1 증가시키고 true를 반환한다.
     * <p>
     * 동시 요청으로 unique 제약을 위반하면 {@code DataIntegrityViolationException}이 이 트랜잭션 밖으로
     * 전파되며, insert와 count 증가가 함께 롤백된다. 예외 흡수(멱등 처리)는 트랜잭션 경계 밖의 Facade가 담당한다.
     * (같은 트랜잭션 내부에서 catch하면 rollback-only로 마킹돼 커밋 시 UnexpectedRollbackException이 발생하기 때문)
     *
     * @return 새 좋아요가 insert되면 true, 이미 존재하면 false
     */
    @Transactional
    public boolean like(Long userId, Long productId) {
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        productLikeRepository.save(new ProductLikeModel(userId, productId));
        productService.increaseLikeCount(productId);
        return true;
    }

    /**
     * 좋아요 취소 + 비정규화 count 감소를 하나의 트랜잭션으로 처리한다.
     * 좋아요 상태가 아니면 무시한다(멱등). 1행 삭제된 경우에만 count를 1 감소시킨다(음수 가드 포함).
     *
     * @return 1행 삭제되면 true, 좋아요 상태가 아니면 false
     */
    @Transactional
    public boolean unlike(Long userId, Long productId) {
        boolean deleted = productLikeRepository.deleteByUserIdAndProductId(userId, productId) > 0;
        if (deleted) {
            productService.decreaseLikeCount(productId);
        }
        return deleted;
    }

    @Transactional(readOnly = true)
    public List<Long> getLikedProductIds(Long userId) {
        return productLikeRepository.findLikedProductIds(userId);
    }
}
