package com.loopers.infrastructure.product;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toDomain(ProductJpaEntity entity) {
        return Product.restore(
                entity.getId(),
                entity.getBrandId(),
                entity.getName(),
                Money.of(entity.getPrice()),
                Stock.of(entity.getStock())
        );
    }

    public ProductJpaEntity toJpaEntity(Product domain) {
        return ProductJpaEntity.of(
                domain.getBrandId(),
                domain.getName(),
                domain.getPrice().getAmount(),
                domain.getStock().getQuantity()
        );
    }
}
