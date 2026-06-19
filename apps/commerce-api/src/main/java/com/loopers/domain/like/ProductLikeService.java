package com.loopers.domain.like;

import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;

    public boolean likeProduct(String userLoginId, Long productId) {
        Optional<ProductLike> existingLike = productLikeRepository.find(userLoginId, productId);
        ProductLikeResult result = createLike(userLoginId, productId, existingLike);

        if (result.created()) {
            productLikeRepository.save(result.productLike());
        }
        return result.created();
    }

    public boolean unlikeProduct(String userLoginId, Long productId) {
        Optional<ProductLike> existingLike = productLikeRepository.find(userLoginId, productId);
        if (existingLike.isEmpty()) {
            return false;
        }

        boolean deleted = deleteLike(existingLike);
        if (deleted) {
            productLikeRepository.delete(existingLike.get());
        }
        return deleted;
    }

    public List<ProductLike> getProductLikes(String userLoginId) {
        return productLikeRepository.findAllByUserLoginId(userLoginId);
    }

    public ProductLikeResult createLike(
        String userLoginId,
        Long productId,
        Optional<ProductLike> existingLike
    ) {
        if (existingLike.isPresent()) {
            return new ProductLikeResult(existingLike.get(), false);
        }

        ProductLike productLike = new ProductLike(userLoginId, productId);
        return new ProductLikeResult(productLike, true);
    }

    public boolean deleteLike(Optional<ProductLike> existingLike) {
        return existingLike.isPresent();
    }

    public List<Product> getLikedProducts(
        List<ProductLike> productLikes,
        List<Product> products
    ) {
        Map<Long, Product> productsById = products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        return productLikes.stream()
            .map(ProductLike::getProductId)
            .distinct()
            .map(productsById::get)
            .filter(Objects::nonNull)
            .toList();
    }

}
