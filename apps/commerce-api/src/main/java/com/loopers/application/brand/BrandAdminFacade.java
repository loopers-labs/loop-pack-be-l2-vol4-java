package com.loopers.application.brand;

import com.loopers.application.product.ProductRepository;
import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BrandAdminFacade {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Long registerBrand(String name) {
        BrandModel brand = new BrandModel(name);
        return brandRepository.save(brand).getId();
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getBrands() {
        return brandRepository.findAll();
    }

    @Transactional
    public void updateBrand(Long id, String name) {
        BrandModel brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        brand.update(name);
        brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        BrandModel brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        brand.delete();
        brandRepository.save(brand);
        productRepository.deleteByBrandId(brandId);
    }
}
