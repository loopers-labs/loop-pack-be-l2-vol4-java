package com.loopers.domain.product;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public Product createProduct(Long brandId, String name, String description, long price) {
        Product product = Product.create(brandId, name, description, price);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findActiveById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    @Transactional(readOnly = true)
    public PageResult<Product> getProducts(PageQuery query, Long brandId) {
        return productRepository.findActiveAll(query, brandId);
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = getProduct(productId);
        product.delete();
    }
}
