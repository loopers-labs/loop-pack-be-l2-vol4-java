package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getActive(productId);

        if (likeService.like(userId, productId)) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        productService.getActive(productId);

        if (likeService.unlike(userId, productId)) {
            productService.decrementLikeCount(productId);
        }
    }

    @Transactional(readOnly = true)
    public List<LikedProductInfo> getMyLikes(Long userId, int page, int size) {
        List<ProductModel> products = likeService.getMyLikedActiveProducts(userId, page, size);
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).toList();
        Map<Long, BrandModel> brandById = brandService.getAllByIdIn(brandIds).stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
        return products.stream()
            .map(product -> LikedProductInfo.from(product, brandById.get(product.getBrandId())))
            .toList();
    }
}
