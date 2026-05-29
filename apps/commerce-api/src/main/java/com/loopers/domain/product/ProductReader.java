package com.loopers.domain.product;

/**
 * 타 도메인(Cart, Like, Order 등)이 Product 정보를 읽을 때 사용하는 읽기 전용 창구.
 * Service가 타 도메인의 Repository를 직접 주입받으면 레이어 경계가 무너지므로,
 * Reader를 통해 의존 방향을 명확히 한다.
 */
public interface ProductReader {
    ProductModel getProduct(Long productId);
    boolean existsProduct(Long productId);
}
