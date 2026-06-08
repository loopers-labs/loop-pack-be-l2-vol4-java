package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandApplicationService {

    private final BrandDomainService brandDomainService;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    // 브랜드 목록 조회
    @Transactional(readOnly = true)
    public Page<Brand> getBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    // 브랜드 단건 조회
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

    // 브랜드 수정
    @Transactional
    public Brand updateBrand(Long brandId, String name) {
        Brand brand = brandDomainService.getBrand(brandId);
        brandDomainService.validateDuplicateNameExcluding(name, brandId);
        brand.update(name);
        return brand;
    }

    // 브랜드 삭제 (cascade: 상품 → 재고 → 브랜드 순으로 소프트딜리트)
    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = brandDomainService.getBrand(brandId);
        List<Long> productIds = productRepository.findIdsByBrandId(brandId);
        if (!productIds.isEmpty()) {
            stockRepository.softDeleteAllByProductIdIn(productIds);
            productRepository.softDeleteAllByBrandId(brandId);
        }
        brand.delete();
    }
}
