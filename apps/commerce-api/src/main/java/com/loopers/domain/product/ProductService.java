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
    public ProductModel createProduct(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = new ProductModel(brandId, name, description, imageUrl, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /** 대고객 조회: 활성 상품만 반환. 없거나 비활성이면 NOT_FOUND (01 §7.4, UC-04). */
    @Transactional(readOnly = true)
    public ProductModel getActiveProduct(Long id) {
        return productRepository.find(id)
            .filter(ProductModel::isActive)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = getProduct(id);
        product.update(name, description, imageUrl, price, stock);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id); // 존재 여부 확인
        productRepository.delete(id);
    }

    /** 좋아요 수 동기 +1 (01 §7.3, 04 §4.2 — 좋아요 등록과 동일 트랜잭션). */
    @Transactional
    public void increaseLikesCount(Long id) {
        ProductModel product = getProduct(id);
        product.incrementLikesCount();
        productRepository.save(product);
    }

    /** 좋아요 수 동기 -1 (음수 방지는 ProductModel 책임). */
    @Transactional
    public void decreaseLikesCount(Long id) {
        ProductModel product = getProduct(id);
        product.decrementLikesCount();
        productRepository.save(product);
    }
}
