package com.loopers.application.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional
    public void addLike(Long userId, Long productId) {
        likeService.like(userId, productId);
        productService.incrementLikeCount(productId);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
        productService.decrementLikeCount(productId);
    }

    public Page<LikeInfo> getLikedProducts(Long userId, Pageable pageable) {
        return likeService.getLikedProducts(userId, pageable)
                .map(like -> {
                    ProductEntity product = productService.getProduct(like.getProductId());
                    return LikeInfo.from(product, brandService.getBrand(product.getBrandId()));
                });
    }
}
