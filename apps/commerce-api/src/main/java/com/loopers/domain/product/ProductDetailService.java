package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 상세에 필요한 도메인 객체 조합을 담당하는 Domain Service.
 *
 * <p>{@link ProductModel}과 {@link BrandModel} 두 도메인 객체의 협력 로직을
 * Application 계층으로 새지 않도록 도메인 계층에 위치시킨다.
 *
 * <p>책임:
 * <ul>
 *   <li>상품 조회 (존재/삭제 여부 검증은 {@link ProductService}가 담당)</li>
 *   <li>상품이 속한 브랜드 조회 ({@link BrandService}가 담당)</li>
 *   <li>두 도메인 객체를 {@link ProductWithBrand}로 묶어 반환</li>
 * </ul>
 *
 * <p>재고 정보, 좋아요 수 등 다른 애그리거트는 Application Facade에서 별도로
 * 조립한다. 본 서비스는 Product-Brand 협력에만 집중한다.
 */
@RequiredArgsConstructor
@Service
public class ProductDetailService {

    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public ProductWithBrand assemble(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return new ProductWithBrand(product, brand);
    }
}
