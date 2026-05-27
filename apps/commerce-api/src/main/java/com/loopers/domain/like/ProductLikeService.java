package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProductLikeService {

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

    public List<ProductModel> getLikedProducts(
        List<ProductLikeModel> productLikes,
        List<ProductModel> products
    ) {
        Map<Long, ProductModel> productsById = products.stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        return productLikes.stream()
            .map(ProductLikeModel::getProductId)
            .distinct()
            .map(productsById::get)
            .filter(Objects::nonNull)
            .toList();
    }
}
