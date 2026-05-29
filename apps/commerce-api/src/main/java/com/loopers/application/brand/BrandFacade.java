package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 브랜드 유스케이스 Facade.
 *
 * <p>스타일 2: Application Layer 가 조회/저장/협력을 모두 책임진다.
 * Brand 도메인은 CRUD 위주라 별도 Domain Service 없이 Facade 가 직접 처리한다.
 */
@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        BrandModel brand = new BrandModel(name, description);
        BrandModel saved = brandRepository.save(brand);
        return BrandInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long brandId) {
        BrandModel brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public List<BrandInfo> getBrands(int page, int size) {
        return brandRepository.findAll(page, size).stream()
            .map(BrandInfo::from)
            .toList();
    }

    @Transactional
    public BrandInfo updateBrand(Long brandId, String name, String description) {
        BrandModel brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
        brand.update(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    /**
     * 브랜드 삭제 시 연관 상품도 cascade soft delete.
     *
     * <p>같은 트랜잭션에서 처리되어 중간 실패 시 전체 롤백.
     * Brand-Product 협력 흐름이지만 단순 cascade라 Domain Service 없이 Facade 가 조율한다.
     */
    @Transactional
    public void deleteBrand(Long brandId) {
        BrandModel brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));

        List<ProductModel> products = productRepository.findAllByBrandId(brandId);
        for (ProductModel product : products) {
            product.delete();
            productRepository.save(product);
        }

        brand.delete();
        brandRepository.save(brand);
    }
}
