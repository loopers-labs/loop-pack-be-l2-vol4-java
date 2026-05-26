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
    public BrandModel createBrand(String name, String description) {
        BrandModel brand = new BrandModel(name, description);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getAllBrands() {
        return brandRepository.findAll();
    }

    @Transactional
    public BrandModel updateBrand(Long id, String name, String description) {
        BrandModel brand = getBrand(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        BrandModel brand = getBrand(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
