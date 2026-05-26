package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductService productService;

    @Transactional
    public ProductLikeModel likeProduct(String userLoginId, Long productId) {
        ProductModel product = productService.getProduct(productId);

        return productLikeRepository.find(userLoginId, productId)
            .orElseGet(() -> saveNewLike(userLoginId, productId, product));
    }

    @Transactional
    public void unlikeProduct(String userLoginId, Long productId) {
        productLikeRepository.find(userLoginId, productId)
            .ifPresent(productLike -> deleteLike(productLike, productId));
    }

    private ProductLikeModel saveNewLike(String userLoginId, Long productId, ProductModel product) {
        ProductLikeModel productLike = new ProductLikeModel(userLoginId, productId);
        product.increaseLikeCount();
        ProductLikeModel savedProductLike = productLikeRepository.save(productLike);
        productService.saveProduct(product);
        return savedProductLike;
    }

    private void deleteLike(ProductLikeModel productLike, Long productId) {
        ProductModel product = productService.getProduct(productId);
        product.decreaseLikeCount();
        productLikeRepository.delete(productLike);
        productService.saveProduct(product);
    }
}
