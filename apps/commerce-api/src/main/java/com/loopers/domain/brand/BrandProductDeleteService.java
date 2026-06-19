package com.loopers.domain.brand;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandProductDeleteService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandProductDeleteResult deleteBrand(Long brandId) {
        Brand brand = getBrand(brandId);
        List<Product> products = productRepository.findAllByBrandId(brandId);
        deleteBrandWithProducts(brand, products);
        brandRepository.save(brand);
        products.forEach(productRepository::save);
        return new BrandProductDeleteResult(brand, products);
    }

    public void deleteBrandWithProducts(Brand brand, List<Product> products) {
        brand.delete();
        products.forEach(Product::delete);
    }

    private Brand getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
