package com.loopers.application.product;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductDomainService;
import com.loopers.domain.stock.service.StockDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductApplicationService {

    private final BrandDomainService brandDomainService;
    private final ProductDomainService productDomainService;
    private final StockDomainService stockDomainService;

    @Transactional
    public Product createProduct (Long brandId, String name, String description, Long price, int initialQuantity){
        brandDomainService.getBrand(brandId);
        Product product = productDomainService.createProduct(brandId, name, description, price);
        stockDomainService.createStock(product.getId(), initialQuantity);
        return product;
    }
}
