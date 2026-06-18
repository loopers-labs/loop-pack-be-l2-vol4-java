package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.cache.ProductCacheService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class BrandApplicationService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;

    @Transactional
    public BrandInfo create(String name, String description) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        return BrandInfo.from(brandRepository.save(new BrandModel(name, description)));
    }

    @Transactional(readOnly = true)
    public BrandInfo get(Long id) {
        return BrandInfo.from(brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다.")));
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getAll(Pageable pageable) {
        return brandRepository.findAll(pageable).map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo update(Long id, String name, String description) {
        BrandModel brand = brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
        brand.update(name, description);
        BrandInfo result = BrandInfo.from(brandRepository.save(brand));
        productCacheService.evictBrand(id);
        productCacheService.evictAllProductLists();
        return result;
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteByBrandId(id);
        brandRepository.delete(id);
        productCacheService.evictBrand(id);
        productCacheService.evictAllProductLists();
    }
}
