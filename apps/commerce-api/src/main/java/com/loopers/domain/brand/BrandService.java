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
    public BrandModel createBrand(String name, String description) {
        return brandRepository.save(new BrandModel(name, description));
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional
    public BrandModel updateBrand(Long id, String name, String description) {
        BrandModel brand = getBrand(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        if (!brandRepository.existsById(id)) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        brandRepository.delete(id);
    }
}
