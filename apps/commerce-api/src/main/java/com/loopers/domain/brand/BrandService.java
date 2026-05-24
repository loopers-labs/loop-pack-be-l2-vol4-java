package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public Brand createBrand(String name, String description) {
        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand getBrand(Long brandId) {
        return brandRepository.findActiveById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    @Transactional(readOnly = true)
    public Page<Brand> getBrands(Pageable pageable) {
        return brandRepository.findActiveAll(pageable);
    }

    @Transactional
    public Brand updateBrand(Long brandId, String name, String description) {
        Brand brand = getBrand(brandId);
        brand.update(name, description);
        return brand;
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = getBrand(brandId);
        brand.delete();
    }
}
