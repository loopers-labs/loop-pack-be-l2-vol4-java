package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;

    @Transactional
    public void likeProduct(String userLoginId, Long productId) {
        productLikeService.likeProduct(userLoginId, productId);
    }

    @Transactional
    public void unlikeProduct(String userLoginId, Long productId) {
        productLikeService.unlikeProduct(userLoginId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(String userLoginId) {
        return productLikeService.getLikedProductDetailViews(userLoginId).stream()
            .map(ProductInfo::from)
            .toList();
    }
}
