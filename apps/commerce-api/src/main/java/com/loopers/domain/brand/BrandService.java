package com.loopers.domain.brand;

import com.loopers.domain.common.PageCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand createBrand(String name, String description) {
        return brandRepository.save(new Brand(name, description));
    }

    public Brand getBrand(Long id) {
        return getBrandById(id);
    }

    public List<Brand> getBrands(Integer page, Integer size) {
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return brandRepository.findAll(pageCriteria.page(), pageCriteria.size());
    }

    public List<Brand> getBrandsByIds(List<Long> ids) {
        return brandRepository.findAllByIds(ids);
    }

    public void validateBrandExists(Long id) {
        getBrandById(id);
    }

    public Brand updateBrand(Long id, String name, String description) {
        Brand brand = getBrandById(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    private Brand getBrandById(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
