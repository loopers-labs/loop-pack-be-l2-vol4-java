package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        return BrandInfo.from(brandRepository.save(new BrandModel(name, description)));
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        BrandModel brand = brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
        return BrandInfo.from(brand);
    }
}
