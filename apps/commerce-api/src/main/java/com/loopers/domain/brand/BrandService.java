package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public Brand getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Brand> getBrands(int page, int size) {
        return brandRepository.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public boolean existsBrand(Long id) {
        return id != null && brandRepository.existsById(id);
    }

    @Transactional
    public Brand updateBrand(Long id, String name, String description) {
        Brand brand = getBrand(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        getBrand(id); // 존재 여부 확인
        brandRepository.delete(id);
    }
}
