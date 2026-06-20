package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
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

    @Transactional(readOnly = true)
    public Brand getBrand(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드 입니다."));
    }

    public Brand getBrandForUpdate(Long id) {
        return brandRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드 입니다."));
    }

    @Transactional(readOnly = true)
    public Page<Brand> getAllBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional
    public Brand createBrand(String name, String description) {
        Brand brand = new Brand(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand updateBrand(Long id, String name, String description) {
        Brand brand = getBrand(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = getBrandForUpdate(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
