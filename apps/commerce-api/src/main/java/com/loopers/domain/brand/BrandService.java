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
    public BrandModel createBrand(String name, String description, String imageUrl) {
        return brandRepository.save(new BrandModel(name, description, imageUrl));
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<BrandModel> findBrand(Long id) {
        return brandRepository.find(id);
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getAllBrands() {
        return brandRepository.findAll();
    }

    @Transactional
    public BrandModel updateBrand(Long id, String name, String description, String imageUrl) {
        BrandModel brand = getBrand(id);
        brand.update(name, description, imageUrl);
        return brand;
    }

    @Transactional
    public void deleteBrand(Long id) {
        getBrand(id);
        brandRepository.delete(id);
    }

    @Transactional
    public void suspendBrand(Long id) {
        getBrand(id).suspend();
    }

    @Transactional
    public void reinstateBrand(Long id) {
        getBrand(id).reinstate();
    }
}
