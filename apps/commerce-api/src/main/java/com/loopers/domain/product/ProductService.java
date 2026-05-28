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
    public ProductEntity createProduct(Long brandId, String name, String description, Long price) {
        return productRepository.save(new ProductEntity(brandId, name, description, price));
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getAllProducts(Long brandId, Pageable pageable) {
        return productRepository.findAll(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsByBrand(Long brandId) {
        return productRepository.findIdsByBrandId(brandId);
    }

    @Transactional
    public ProductEntity updateProduct(Long id, String name, String description, Long price) {
        ProductEntity product = getProduct(id);
        product.update(name, description, price);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductEntity product = getProduct(id);
        product.delete();
        productRepository.save(product);
    }

    @Transactional
    public void deleteAll(List<Long> ids) {
        productRepository.findAllByIds(ids).forEach(product -> {
            product.delete();
            productRepository.save(product);
        });
    }
}
