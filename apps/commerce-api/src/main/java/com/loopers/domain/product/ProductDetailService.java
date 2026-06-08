package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.springframework.stereotype.Service;

/**
 * 상품 상세에 필요한 도메인 객체 조합을 담당하는 Domain Service.
 *
 * <p><strong>스타일 2 (순수형 Domain Service)</strong>: Repository 의존 없이
 * 호출자가 조회해서 넘긴 도메인 객체만 받아 협력시킨다. 모든 영속성 호출은
 * 호출자({@link com.loopers.application.product.ProductFacade})가 책임진다.
 *
 * <p>현재는 두 객체를 묶기만 하는 단순 조립이지만, 추후 도메인 협력 규칙
 * (예: "삭제된 브랜드의 상품은 상세 조회 불가" 같은 정책)이 추가될 자리이다.
 */
@Service
public class ProductDetailService {

    public ProductWithBrand assemble(ProductModel product, BrandModel brand) {
        return new ProductWithBrand(product, brand);
    }
}
