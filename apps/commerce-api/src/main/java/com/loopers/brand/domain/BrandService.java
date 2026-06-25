package com.loopers.brand.domain;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    public Brand getBrand(Long brandId) {
        return brandRepository.findActiveById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    @Transactional(readOnly = true)
    public List<Brand> getBrands(Collection<Long> brandIds) {
        return brandRepository.findActiveAllByIds(brandIds);
    }

    @Transactional(readOnly = true)
    public List<Brand> getAllByIds(Collection<Long> brandIds) {
        List<Brand> brands = getBrands(brandIds);
        if (brands.size() != Set.copyOf(brandIds).size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다.");
        }
        return brands;
    }

    @Transactional(readOnly = true)
    public PageResult<Brand> getBrands(PageQuery query) {
        return brandRepository.findActiveAll(query);
    }

    @Transactional
    public Brand updateBrand(Long brandId, String name, String description) {
        Brand brand = getBrand(brandId);
        brand.update(name, description);
        return brand;
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = getBrand(brandId);
        brand.delete();
    }
}
