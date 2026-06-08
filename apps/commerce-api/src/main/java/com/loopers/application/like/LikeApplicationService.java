package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        likeRepository.save(new LikeModel(userId, productId));
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 내역이 없습니다."));
        likeRepository.delete(like.getId());
    }

    @Transactional(readOnly = true)
    public List<LikeInfo> getLikedProducts(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
            .map(like -> {
                ProductModel product = productRepository.find(like.getProductId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + like.getProductId() + "] 상품을 찾을 수 없습니다."));
                long likeCount = likeRepository.countByProductId(like.getProductId());
                return LikeInfo.from(like, product, likeCount);
            })
            .toList();
    }
}
