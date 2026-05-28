package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class BrandApplicationService {

    private final BrandDomainService brandDomainService;
    private final BrandRepository brandRepository;

    // 브랜드 조회
    @Transactional(readOnly = true)
    public Brand getBrand(Long brandId) {
        return brandDomainService.getBrand(brandId);
    }

    // 브랜드 등록
    @Transactional
    public Brand register(String name) {
        brandDomainService.validateDuplicateName(name);
        Brand brand = Brand.create(name);
        return brandRepository.save(brand);
    }
}
