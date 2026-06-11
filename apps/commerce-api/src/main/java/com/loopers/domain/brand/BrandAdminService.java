package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandAdminService {

    private final BrandRepository brandRepository;

    public Long registerBrand(String name) {
        BrandModel brand = new BrandModel(name);
        return brandRepository.save(brand).getId();
    }

    public List<BrandModel> getBrands() {
        return brandRepository.findAll();
    }

    @Transactional
    public void updateBrand(Long id, String name) {
        BrandModel brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        brand.update(name);
    }

    @Transactional
    public void deleteBrand(Long id) {
        BrandModel brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        brand.delete();
    }
}
