package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;

    public void addLike(Long userId, Long productId) {
        likeService.like(userId, productId);
        productService.incrementLikeCount(productId);
    }

    public void removeLike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
        productService.decrementLikeCount(productId);
    }

    public Page<ProductInfo> getLikedProducts(Long authUserId, Long pathUserId, Pageable pageable) {
        if (!authUserId.equals(pathUserId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        return likeService.getLikedProducts(pathUserId, pageable)
                .map(like -> {
                    ProductEntity product = productService.getProduct(like.getProductId());
                    BrandEntity brand = brandService.getBrand(product.getBrandId());
                    InventoryEntity inventory = inventoryService.getByProductId(product.getId());
                    return ProductInfo.from(product, brand, inventory);
                });
    }
}
