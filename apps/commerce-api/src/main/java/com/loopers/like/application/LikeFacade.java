package com.loopers.like.application;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRepository;
import com.loopers.like.domain.LikeService;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final LikeRepository likeRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;

    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        productService.getOrThrow(productRepository.find(productId));

        if (likeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }

        LikeModel like = new LikeModel(userId, productId);
        return LikeInfo.from(likeRepository.save(like));
    }

    @Transactional
    public void cancelLike(Long userId, Long productId) {
        LikeModel like = likeService.getOrThrow(likeRepository.findByUserIdAndProductId(userId, productId));
        likeRepository.delete(like);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
            .map(like -> productRepository.find(like.getProductId()))
            .flatMap(java.util.Optional::stream)
            .map(ProductInfo::from)
            .toList();
    }
}
