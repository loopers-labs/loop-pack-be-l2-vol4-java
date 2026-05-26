package com.loopers.application.brand;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class BrandFacade {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final BrandRepository brandRepository;

    public BrandCreateInfo createBrand(String name, String description) {
        if (brandRepository.existsActiveByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }

        BrandModel newBrand = BrandModel.builder()
            .rawName(name)
            .rawDescription(description)
            .build();

        return BrandCreateInfo.from(brandRepository.save(newBrand));
    }

    public BrandUpdateInfo updateBrand(Long brandId, String name, String description) {
        BrandModel brand = brandRepository.getActiveById(brandId);

        if (brandRepository.existsActiveByNameAndIdNot(name, brandId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }

        brand.update(name, description);

        return BrandUpdateInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo readBrand(Long brandId) {
        BrandModel brand = brandRepository.getActiveById(brandId);

        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> readBrands(int page, int size) {
        if (page < 0 || size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("페이지 번호는 0 이상, 크기는 %d~%d만 허용됩니다.", MIN_PAGE_SIZE, MAX_PAGE_SIZE));
        }

        Page<BrandModel> brandModels = brandRepository.findActiveByPage(page, size);

        return brandModels.map(BrandInfo::from);
    }
}
