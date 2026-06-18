package com.loopers.domain.product;

import com.loopers.config.CacheConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    public ProductModel create(BrandModel brand, String name, String description, Long price) {
        return productRepository.save(new ProductModel(brand, name, description, price));
    }

    /** 어드민용 — 삭제된 상품 포함 */
    public ProductModel get(UUID id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /** 고객용 — 활성 상품만 */
    public ProductModel getActive(UUID id) {
        return productRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /**
     * 고객 상세 조회 캐시용 — brand LAZY 로드는 호출부(ProductFacade)의 @Transactional 범위에서 처리됨.
     * like/unlike 발생 시 @CacheEvict 필수 (LikeFacade).
     */
    @Cacheable(value = CacheConfig.PRODUCT_CACHE, key = "#id")
    public ProductCacheDto getActiveSnapshot(UUID id) {
        ProductModel product = productRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
        return ProductCacheDto.from(product);
    }

    /** 어드민 목록 — brandId null이면 전체, 있으면 브랜드 필터 */
    public Page<ProductModel> getList(UUID brandId, Pageable pageable) {
        if (brandId != null) {
            return productRepository.findAllByBrandIdPaged(brandId, pageable);
        }
        return productRepository.findAll(pageable);
    }

    /** 고객 목록 — 활성 상품만, brandId null이면 전체, 있으면 브랜드 필터 */
    public Page<ProductModel> getActiveList(UUID brandId, Pageable pageable) {
        if (brandId != null) {
            return productRepository.findAllActiveByBrandId(brandId, pageable);
        }
        return productRepository.findAllActive(pageable);
    }

    public ProductModel update(UUID id, String name, String description, Long price) {
        ProductModel product = get(id);
        product.update(name, description, price);
        return product;
    }

    public void delete(UUID id) {
        ProductModel product = get(id);
        product.delete();
    }

    /**
     * 브랜드 소프트딜리트 시 cascade 처리.
     * BrandFacade.delete()에서 brandService.delete() 이후 호출.
     */
    public void deleteByBrand(UUID brandId) {
        List<ProductModel> products = productRepository.findAllByBrandId(brandId);
        products.forEach(ProductModel::delete);
    }
}
