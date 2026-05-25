package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;


    /** 브랜드 단건 조회 (brandId) */
    public Brand getBrand(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드 입니다."));
    }

    /** 브랜드 목록조회 (페이징) */
    public Page<Brand> getAllBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    public Brand createBrand(String name, String description) {
        Brand brand = new Brand(name, description);
        return brandRepository.save(brand);
    }

    public Brand updateBrand(Long id, String name, String description) {
        Brand brand = getBrand(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    public void deleteBrand(Long id) {
        Brand brand = getBrand(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
