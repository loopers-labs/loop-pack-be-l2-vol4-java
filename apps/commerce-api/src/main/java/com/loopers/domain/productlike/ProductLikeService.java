package com.loopers.domain.productlike;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;

    /**
     * 좋아요를 멱등하게 등록한다. 이미 좋아요 상태면 아무 일도 하지 않고 false를 반환한다.
     * <p>
     * insert IGNORE로 unique 제약 위반을 예외 없이 흡수하므로, 동시 중복 요청에도 rollback-only 오염이 없다.
     * 비정규화 like_count 집계는 이 메서드의 책임이 아니다 — 좋아요 커밋 후 이벤트 리스너가 별도로 처리한다.
     * 트랜잭션 경계는 호출자(Facade)가 소유한다.
     *
     * @return 새로 좋아요가 등록되면 true, 이미 존재하면 false
     */
    public boolean like(Long userId, Long productId) {
        return productLikeRepository.insertIgnore(userId, productId) > 0;
    }

    /**
     * 좋아요를 멱등하게 취소한다. 좋아요 상태가 아니면 무시하고 false를 반환한다.
     * like_count 감소 역시 이 메서드가 아니라 이벤트 리스너가 처리한다.
     *
     * @return 1행 삭제되면 true, 좋아요 상태가 아니면 false
     */
    public boolean unlike(Long userId, Long productId) {
        return productLikeRepository.deleteByUserIdAndProductId(userId, productId) > 0;
    }

    @Transactional(readOnly = true)
    public List<Long> getLikedProductIds(Long userId) {
        return productLikeRepository.findLikedProductIds(userId);
    }
}
