package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel createProduct(String name, String description, Long price, Long brandId) {
        ProductModel product = new ProductModel(name, description, price, brandId);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND,
                "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public ProductModel getActive(Long id) {
        return productRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND,
                "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getAllActiveProducts(Long brandId, ProductSortType sort, int page, int size) {
        return productRepository.findAllActive(brandId, sort, page, size);
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getAllProducts(Long brandId, Pageable pageable) {
        return productRepository.findAll(brandId, pageable);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, Long price) {
        ProductModel product = getActive(id);
        product.update(name, description, price);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProduct(id);
        product.delete();
    }

    @Transactional
    public void deleteByBrandId(Long brandId) {
        productRepository.findAllActiveByBrandId(brandId)
            .forEach(ProductModel::delete);
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        int affected = productRepository.incrementLikeCount(productId);
        if (affected == 0) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        int affected = productRepository.decrementLikeCount(productId);
        if (affected == 0) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
    }
}
