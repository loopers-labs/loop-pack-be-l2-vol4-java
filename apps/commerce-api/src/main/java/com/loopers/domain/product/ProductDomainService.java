package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Product 도메인 서비스.
 *
 * - 상태(Repository) 없이 도메인 객체 간 협력 로직만 담당
 * - 여러 유스케이스(ProductService, LikeService, OrderFacade)에서 공통으로 사용하는
 *   Product·Brand 도메인 규칙과 조합 로직을 중앙화한다.
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

    /**
     * 상품 상세 정보 조합.
     * Product(+ Brand) + Stock 정보를 도메인 수준 값 객체로 조립한다.
     * Application Layer는 이를 응답 DTO로 변환하여 노출한다.
     *
     * @param product 조회된 ProductModel (Brand를 포함)
     * @param stock   해당 상품의 StockModel
     * @return Product + Brand + Stock 조합 결과
     */
    public ProductDetail assembleDetail(ProductModel product, StockModel stock) {
        return new ProductDetail(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getBrand().getName(),
            stock.getQuantity(),
            product.getLikeCount()
        );
    }
}
