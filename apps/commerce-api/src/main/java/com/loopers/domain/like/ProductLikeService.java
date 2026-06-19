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

    public boolean likeProduct(String userLoginId, Product product) {
        Optional<ProductLike> existingLike = productLikeRepository.find(userLoginId, product.getId());
        ProductLikeResult result = createLike(userLoginId, product.getId(), product, existingLike);

        if (result.created()) {
            productLikeRepository.save(result.productLike());
        }
        return result.created();
    }

    public boolean unlikeProduct(String userLoginId, Product product) {
        Optional<ProductLike> existingLike = productLikeRepository.find(userLoginId, product.getId());
        if (existingLike.isEmpty()) {
            return false;
        }

        boolean deleted = deleteLike(product, existingLike);
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
        Product product,
        Optional<ProductLike> existingLike
    ) {
        if (existingLike.isPresent()) {
            return new ProductLikeResult(existingLike.get(), false);
        }

        ProductLike productLike = new ProductLike(userLoginId, productId);
        product.increaseLikeCount();
        return new ProductLikeResult(productLike, true);
    }

    public boolean deleteLike(Product product, Optional<ProductLike> existingLike) {
        if (existingLike.isEmpty()) {
            return false;
        }
        product.decreaseLikeCount();
        return true;
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
