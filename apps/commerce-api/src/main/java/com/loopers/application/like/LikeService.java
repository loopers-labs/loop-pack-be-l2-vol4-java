package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long memberId, Long productId) {
        ProductModel product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 상품을 찾을 수 없습니다."));

        boolean alreadyLiked = likeRepository.findByMemberIdAndProductId(memberId, productId).isPresent();
        if (alreadyLiked) {
            return;
        }

        likeRepository.save(new LikeModel(memberId, productId));
        product.increaseLikeCount();
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        LikeModel like = likeRepository.findByMemberIdAndProductId(memberId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 정보를 찾을 수 없습니다."));

        ProductModel product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.delete(like);
        product.decreaseLikeCount();
    }

    @Transactional(readOnly = true)
    public List<LikeModel> getLikesByMemberId(Long memberId) {
        return likeRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<LikeInfo> getLikeInfosByMemberId(Long memberId) {
        return likeRepository.findAllByMemberId(memberId).stream()
            .map(like -> {
                ProductModel product = productRepository.findById(like.getProductId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + like.getProductId() + "] 상품을 찾을 수 없습니다."));
                return LikeInfo.of(like, product);
            })
            .toList();
    }
}
