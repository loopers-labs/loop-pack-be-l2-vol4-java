package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel createProduct(Long brandId, String name, String description, Long price, Integer stock, String imageUrl) {
        return productRepository.save(new ProductModel(brandId, name, description, price, stock, imageUrl));
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getActiveProducts(Long brandId) {
        return productRepository.findAllActive(brandId);
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getActiveProducts(Long brandId, ProductSortType sort) {
        return productRepository.findAllActive(brandId, sort);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, Long price, Integer stock, String imageUrl) {
        ProductModel product = getProduct(id);
        product.update(name, description, price, stock, imageUrl);
        return product;
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id); // 존재 여부 확인
        productRepository.delete(id);
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        ProductModel product = productRepository.findWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        product.incrementLikeCount();
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        ProductModel product = productRepository.findWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        product.decrementLikeCount();
    }
}
