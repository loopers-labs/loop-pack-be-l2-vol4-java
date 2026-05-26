package com.loopers.application.brand;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final ProductFacade productFacade;

    /**
     * 브랜드 삭제 — soft delete + 그 브랜드의 모든 상품, 그리고 각 상품의 좋아요까지 cascade 비활성 (01 §7.5).
     * Brand→Product→Like 전파를 한 트랜잭션으로 묶어 원자적으로 처리한다(한쪽 실패 시 전체 롤백).
     * 상품 단위의 Product→Like 전파는 ProductFacade.deleteProduct를 재사용한다.
     */
    @Transactional
    public void deleteBrand(Long brandId) {
        brandService.deleteBrand(brandId);
        for (ProductModel product : productService.getActiveProductsByBrand(brandId)) {
            productFacade.deleteProduct(product.getId());
        }
    }
}
