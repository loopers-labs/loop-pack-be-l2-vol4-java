package com.loopers.brand.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        BrandModel brand = new BrandModel(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long brandId) {
        BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo updateBrand(Long brandId, String name, String description) {
        BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
        brand.update(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
        List<ProductModel> products = productRepository.findAllByBrandId(brandId);

        // [fix] 브랜드 삭제 시 소속 상품 연쇄 소프트 딜리트 누락
        brandService.deleteCascade(brand, products);

        products.forEach(productRepository::save);
        brandRepository.save(brand);
    }
}
