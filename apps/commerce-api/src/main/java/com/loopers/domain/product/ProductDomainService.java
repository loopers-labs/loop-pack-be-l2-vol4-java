package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Product 도메인 서비스.
 *
 * - 상태(Repository) 없이 도메인 객체 간 협력 로직만 담당
 * - 여러 유스케이스(ProductService, LikeService, OrderFacade)에서 공통으로 사용하는
 *   Product·Brand 도메인 규칙을 중앙화한다.
 */
@Component
public class ProductDomainService {

    /**
     * 브랜드 유효성 검증.
     * 삭제된 브랜드에는 상품을 등록하거나 연결할 수 없다.
     */
    public void validateBrand(BrandModel brand) {
        if (brand.isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 브랜드로는 상품을 등록할 수 없습니다.");
        }
    }

    /**
     * 상품 활성 여부 검증.
     * 삭제된 상품에는 좋아요·주문 등의 행위를 수행할 수 없다.
     */
    public void validateProductActive(ProductModel product) {
        if (product.isDeleted()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품입니다.");
        }
    }
}
