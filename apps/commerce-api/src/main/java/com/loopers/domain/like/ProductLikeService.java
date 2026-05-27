package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductCatalogService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ProductLikeService {

    private final ProductCatalogService productCatalogService = new ProductCatalogService();

    public ProductLikeResult likeProduct(
        String userLoginId,
        Long productId,
        ProductModel product,
        Optional<ProductLikeModel> existingLike
    ) {
        if (existingLike.isPresent()) {
            return new ProductLikeResult(existingLike.get(), false);
        }

        ProductLikeModel productLike = new ProductLikeModel(userLoginId, productId);
        product.increaseLikeCount();
        return new ProductLikeResult(productLike, true);
    }

    public boolean unlikeProduct(ProductModel product, Optional<ProductLikeModel> existingLike) {
        if (existingLike.isEmpty()) {
            return false;
        }
        product.decreaseLikeCount();
        return true;
    }

    public List<ProductDetail> getLikedProductDetails(
        List<ProductLikeModel> productLikes,
        Map<Long, ProductModel> productsById,
        Map<Long, BrandModel> brandsById
    ) {
        return productLikes.stream()
            .map(ProductLikeModel::getProductId)
            .distinct()
            .map(productsById::get)
            .filter(Objects::nonNull)
            .map(product -> productCatalogService.getProductDetails(List.of(product), brandsById).get(0))
            .toList();
    }
}
