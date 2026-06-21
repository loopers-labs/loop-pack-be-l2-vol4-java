package com.loopers.application.catalog.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandQueryService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public BrandResult getActiveBrand(Long brandId) {
        Brand brand = brandRepository.findActive(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));

        return BrandResult.from(brand);
    }

    @Transactional(readOnly = true)
    public PageResult<BrandResult> getAdminBrands(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 20 : size;
        List<BrandResult> items = brandRepository.findAll(normalizedPage, normalizedSize)
            .stream()
            .map(BrandResult::from)
            .toList();

        return PageResult.of(items, normalizedPage, normalizedSize, brandRepository.countAll());
    }

    @Transactional(readOnly = true)
    public BrandResult getAdminBrand(Long brandId) {
        Brand brand = brandRepository.find(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));

        return BrandResult.from(brand);
    }
}
