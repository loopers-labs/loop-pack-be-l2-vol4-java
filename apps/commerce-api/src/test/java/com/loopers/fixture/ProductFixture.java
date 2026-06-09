package com.loopers.fixture;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

/**
 * 상품 테스트 픽스처 — Object Mother 패턴
 */
public class ProductFixture {

    public static final String NAME        = "에어맥스 90";
    public static final String DESCRIPTION = "나이키 클래식 러닝화";
    public static final Long   PRICE       = 150_000L;
    public static final int    INITIAL_QUANTITY = 100;

    public static ProductModel createModel(BrandModel brand) {
        return new ProductModel(brand, NAME, DESCRIPTION, PRICE);
    }
}
