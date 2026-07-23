package com.loopers.tddstudy.interfaces.api.product;

import com.loopers.tddstudy.domain.product.Product;

public class ProductV1Dto {

    // rank 는 순위 없으면 null (체크리스트: "순위에 없다면 null")
    public record ProductDetailResponse(
            Long id, String name, int price, int stock, String status, Long rank
    ) {
        public static ProductDetailResponse of(Product product, Long rank) {
            return new ProductDetailResponse(
                    product.getId(), product.getName(), product.getPrice(),
                    product.getStock(), product.getStatus(), rank
            );
        }
    }
}
