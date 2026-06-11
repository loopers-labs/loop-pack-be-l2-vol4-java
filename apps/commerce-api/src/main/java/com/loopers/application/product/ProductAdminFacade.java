package com.loopers.application.product;

import com.loopers.domain.product.ProductAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ProductAdminFacade {

    private final ProductAdminService productAdminService;

    @Transactional
    public Long registerProduct(Long brandId, String name, BigDecimal price, int initialStock) {
        return productAdminService.registerProduct(brandId, name, price, initialStock);
    }

    public void updateProduct(Long id, String name, BigDecimal price) {
        productAdminService.updateProduct(id, name, price);
    }

    public void deleteProduct(Long id) {
        productAdminService.deleteProduct(id);
    }
}
