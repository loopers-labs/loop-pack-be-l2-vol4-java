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
    public Product createProduct(String name, String description, Long price, Integer stock) {
        Product product = Product.builder()
            .name(name)
            .description(description)
            .price(price)
            .stock(stock)
            .build();
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public Product updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = getProduct(id);
        product.update(name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        getProduct(id); // 존재 여부 확인
        productRepository.delete(id);
    }
}
