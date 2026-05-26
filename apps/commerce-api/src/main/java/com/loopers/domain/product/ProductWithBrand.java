package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

/**
 * 브랜드와 묶인 상품을 표현하는 도메인 객체.
 *
 * <p>두 도메인 객체({@link ProductModel}, {@link BrandModel})가 함께 다뤄질 때의
 * 단위를 명시적으로 표현한다. {@link ProductDetailService}에서 조립되어
 * Application 계층으로 전달된다.
 *
 * <p>UI 응답용 DTO가 아닌, 도메인 협력 결과를 담는 도메인 객체이다.
 *
 * <p>본 record는 {@link ProductDetailService} 에서만 생성되며, 두 인자 모두
 * 호출자({@code productService.getProduct} / {@code brandService.getBrand})가
 * 존재성을 보장한다. 별도 null 검증은 두지 않는다.
 */
public record ProductWithBrand(ProductModel product, BrandModel brand) {}
