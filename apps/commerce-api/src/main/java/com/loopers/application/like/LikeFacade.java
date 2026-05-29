package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandReader;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductReader;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStock;
import com.loopers.domain.product.ProductStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final ProductReader productReader;
    private final ProductStockService productStockService;
    private final BrandReader brandReader;

    @Transactional
    public void addLike(Long userId, Long productId) {
        productReader.getProduct(productId);          // 상품 존재 확인
        likeService.addLike(userId, productId);
        productService.increaseLikeCount(productId);  // @Modifying으로 원자 업데이트
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        productReader.getProduct(productId);          // 상품 존재 확인
        likeService.removeLike(userId, productId);
        productService.decreaseLikeCount(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(Long userId) {
        return likeService.getLikedProductIds(userId).stream()
            .map(productId -> {
                ProductModel product = productReader.getProduct(productId);
                ProductStock stock = productStockService.getStock(productId);
                Brand brand = brandReader.getBrand(product.getBrandId());
                return ProductInfo.of(product, stock, brand);
            })
            .toList();
    }
}
