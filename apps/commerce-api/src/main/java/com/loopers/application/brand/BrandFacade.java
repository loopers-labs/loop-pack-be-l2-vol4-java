package com.loopers.application.brand;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public Page<BrandInfo> getList(Pageable pageable) {
        return brandService.getList(pageable).map(BrandInfo::from);
    }

    public BrandInfo getBrand(Long brandId) {
        return BrandInfo.from(brandService.get(brandId));
    }

    public BrandInfo register(String name) {
        return BrandInfo.from(brandService.create(name));
    }

    public BrandInfo update(Long brandId, String name) {
        return BrandInfo.from(brandService.update(brandId, name));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LATEST_PRICE_CACHE, allEntries = true),
            @CacheEvict(cacheNames = RedisConfig.PRODUCT_LIST_LIKES_CACHE, allEntries = true)
    })
    @Transactional
    public void delete(Long brandId) {
        brandService.delete(brandId);
        productService.suspendAllByBrandId(brandId);
    }
}
