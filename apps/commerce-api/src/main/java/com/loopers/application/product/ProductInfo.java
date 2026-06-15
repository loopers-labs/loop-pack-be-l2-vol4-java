package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDetail;

public final class ProductInfo {

    private ProductInfo() {
    }

    public record Detail(
            Long id,
            Long brandId,
            String brandName,
            String name,
            long price,
            int stock,
            long likeCount,
            boolean soldOut
    ) {

        public static Detail from(ProductDetail detail) {
            Product product = detail.product();
            return new Detail(
                    product.getId(),
                    detail.brandId(),
                    detail.brandName(),
                    product.getName(),
                    product.getPrice().getAmount(),
                    detail.stockQuantity(),
                    detail.likeCount(),
                    detail.soldOut()
            );
        }
    }

    public record ListItem(
            Long id,
            Long brandId,
            String brandName,
            String name,
            long price,
            long likeCount,
            boolean soldOut
    ) {

        public static ListItem from(ProductDetail detail) {
            Product product = detail.product();
            return new ListItem(
                    product.getId(),
                    detail.brandId(),
                    detail.brandName(),
                    product.getName(),
                    product.getPrice().getAmount(),
                    detail.likeCount(),
                    detail.soldOut()
            );
        }
    }

    public record Created(
            Long id,
            Long brandId,
            String name,
            long price,
            int stock
    ) {

        public static Created from(Product product, int stock) {
            return new Created(
                    product.getId(),
                    product.getBrandId(),
                    product.getName(),
                    product.getPrice().getAmount(),
                    stock
            );
        }
    }
}
