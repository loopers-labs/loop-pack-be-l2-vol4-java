package com.loopers.domain.like;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
    private final ProductRepository productRepository;

    public void likeProduct(String userLoginId, Long productId) {
        Product product = getProduct(productId);
        Optional<ProductLike> existingLike = productLikeRepository.find(userLoginId, productId);
        ProductLikeResult result = createLike(userLoginId, productId, product, existingLike);

        if (result.created()) {
            productLikeRepository.save(result.productLike());
            productRepository.save(product);
        }
    }

    public void unlikeProduct(String userLoginId, Long productId) {
        Optional<ProductLike> existingLike = productLikeRepository.find(userLoginId, productId);
        if (existingLike.isEmpty()) {
            return;
        }

        Product product = getProduct(productId);
        boolean deleted = deleteLike(product, existingLike);
        if (deleted) {
            productLikeRepository.delete(existingLike.get());
            productRepository.save(product);
        }
    }

    public List<Product> getLikedProducts(String userLoginId) {
        List<ProductLike> productLikes = productLikeRepository.findAllByUserLoginId(userLoginId);
        List<Long> productIds = productLikes.stream()
            .map(ProductLike::getProductId)
            .distinct()
            .toList();

        List<Product> products = productRepository.findAllByIds(productIds);
        return getLikedProducts(productLikes, products);
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

    private Product getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }
}
