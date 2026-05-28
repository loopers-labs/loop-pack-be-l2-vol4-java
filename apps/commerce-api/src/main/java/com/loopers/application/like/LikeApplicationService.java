package com.loopers.application.like;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void register(Long userId, Long productId) {
        if (productRepository.find(productId).isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
        if (likeRepository.exists(userId, productId)) {
            return;
        }
        likeRepository.save(Like.create(userId, productId));
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        likeRepository.delete(userId, productId);
    }

    @Transactional(readOnly = true)
    public PageResult<LikeInfo> getMyLikes(Long userId, int page, int size) {
        return likeRepository.findAllByUserId(userId, page, size).map(LikeInfo::from);
    }
}
