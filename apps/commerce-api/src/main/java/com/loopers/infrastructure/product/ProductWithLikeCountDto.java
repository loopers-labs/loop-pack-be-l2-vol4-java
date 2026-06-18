package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductWithLikeCountDto {
    private ProductModel product;
    private Long likeCount;
}
