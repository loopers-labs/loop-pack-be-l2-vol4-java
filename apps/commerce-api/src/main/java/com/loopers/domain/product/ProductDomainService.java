package com.loopers.domain.product;

import com.loopers.domain.stock.StockModel;
import org.springframework.stereotype.Component;

/**
 * Product 도메인 서비스.
 *
 * - 상태(Repository) 없이 도메인 객체 간 협력 로직만 담당
 * - 여러 유스케이스에서 공통으로 사용하는 Product·Brand·Stock 조합 로직을 중앙화한다.
 */
@Component
public class ProductDomainService {

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
