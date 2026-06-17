package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductRepository productRepository;

    public void like(Long userId, Long productId) {
        likeService.like(userId, productId);
    }

    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
    }

    public List<ProductInfo> getLikedProducts(Long userId) {
        return likeService.getLikedProducts(userId).stream()
            .map(like -> productRepository.findById(like.getProductId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")))
            .map(ProductInfo::from)
            .toList();
    }
}
