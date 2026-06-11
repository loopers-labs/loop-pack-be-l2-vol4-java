package com.loopers.domain.product;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public Long registerProduct(Long brandId, String name, BigDecimal price, int initialStock) {
        brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        ProductModel product = new ProductModel(brandId, name, price);
        product.assignStock(initialStock);
        
        return productRepository.save(product).getId();
    }

    public void updateProduct(Long id, String name, BigDecimal price) {
        ProductModel product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        product.update(name, price);
        productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        ProductModel product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        product.delete();
        productRepository.save(product);
    }
}
