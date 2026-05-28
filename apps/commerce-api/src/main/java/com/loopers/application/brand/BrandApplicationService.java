package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandApplicationService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandInfo register(BrandCriteria.Register command) {
        Brand brand = Brand.create(command.name(), command.description());
        Brand saved = brandRepository.save(brand);
        return BrandInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        Brand brand = brandRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public List<BrandInfo> getBrandList() {
        return brandRepository.findAll().stream()
                .map(BrandInfo::from)
                .toList();
    }

    @Transactional
    public void modify(BrandCriteria.Modify command) {
        Brand brand = brandRepository.find(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.modify(command.name(), command.description());
        brandRepository.update(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = brandRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.delete();
        brandRepository.update(brand);
        productRepository.bulkSoftDeleteByBrandId(id);
    }
}
