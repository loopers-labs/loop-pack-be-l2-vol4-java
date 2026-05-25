package com.loopers.fixture;

import com.loopers.domain.product.ProductModel;

public class ProductModelFixture {

    private Long brandId = 1L;
    private String name = "에어맥스 90";
    private String description = "클래식 러닝화";
    private String imageUrl = "http://img.example.com/airmax90.png";
    private Long price = 139000L;
    private Integer stock = 10;

    public static ProductModelFixture aProduct() {
        return new ProductModelFixture();
    }

    public ProductModelFixture withBrandId(Long brandId) { this.brandId = brandId; return this; }
    public ProductModelFixture withName(String name) { this.name = name; return this; }
    public ProductModelFixture withDescription(String description) { this.description = description; return this; }
    public ProductModelFixture withImageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
    public ProductModelFixture withPrice(Long price) { this.price = price; return this; }
    public ProductModelFixture withStock(Integer stock) { this.stock = stock; return this; }

    public ProductModel build() {
        return new ProductModel(brandId, name, description, imageUrl, price, stock);
    }
}
