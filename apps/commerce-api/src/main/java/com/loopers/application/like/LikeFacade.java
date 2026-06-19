package com.loopers.application.like;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LikeCountRepository likeCountRepository;

    @Transactional
    public void like(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 이미 좋아요한 경우
        }
        productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.save(new LikeModel(userId, productId));
        likeCountRepository.increase(productId); // 원자적 UPDATE — product 행을 건드리지 않음
    }

    @Transactional
    public void unlike(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 좋아요하지 않은 경우
        }
        likeRepository.deleteBy(userId, productId);
        likeCountRepository.decrease(productId);
    }

    private Long resolveUserId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."))
            .getId();
    }
}
