package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOption;
import com.loopers.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductDetailService productDetailService;

    /**
     * 상품 상세. Product + Brand 조합은 ProductDetailService(Domain Service) 가 책임.
     * Facade 는 흐름과 DTO 변환만 한다.
     */
    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL, key = "#productId")
    public ProductInfo getProductDetail(Long productId) {
        ProductDetailService.ProductWithBrand combined = productDetailService.getProductWithBrand(productId);
        return ProductInfo.from(combined.product(), combined.brand());
    }

    /**
     * 상품 목록. 정렬/필터/페이징 적용. N+1 방지를 위해 brand 는 in-query 한 번에 조회.
     */
    @Cacheable(cacheNames = CacheConfig.PRODUCT_LIST,
            key = "#brandId + ':' + #sort + ':' + #page + ':' + #size")
    public List<ProductInfo> getProductList(Long brandId, ProductSortOption sort, int page, int size) {
        List<ProductModel> products = productRepository.findAll(brandId, sort, page, size);
        if (products.isEmpty()) {
            return new ArrayList<>();
        }

        // brandId 들을 모아서 한 번에 brand 조회 (N+1 방지)
        Set<Long> brandIds = products.stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, BrandModel> brandMap = new HashMap<>();
        for (Long id : brandIds) {
            brandRepository.findById(id).ifPresent(b -> brandMap.put(id, b));
        }

        return products.stream()
                .map(p -> {
                    BrandModel brand = brandMap.get(p.getBrandId());
                    return ProductInfo.from(p, brand);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
