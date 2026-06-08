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
    public ProductModel createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = new ProductModel(brandId, name, description, price, stock);
        return productRepository.save(product);
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
    public ProductPage searchProducts(Long brandId, String sort, String direction, Integer page, Integer size) {
        ProductSearchCondition condition = ProductSearchCondition.of(brandId, sort, direction, page, size);
        return productRepository.search(condition);
    }

    @Transactional
    public ProductModel updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = getProduct(id);
        product.update(brandId, name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional
    public ProductModel deductStock(Long id, int quantity) {
        ProductModel product = getProduct(id);
        product.deductStock(quantity);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id); // 존재 여부 확인
        productRepository.delete(id);
    }
}
