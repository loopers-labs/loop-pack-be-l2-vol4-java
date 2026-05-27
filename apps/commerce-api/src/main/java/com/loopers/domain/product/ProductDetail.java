package com.loopers.domain.product;

/**
 * 상품 상세 정보를 조합한 도메인 값 객체.
 *
 * ProductDomainService.assembleDetail()이 Product + Brand + Stock 정보를 조합하여 반환한다.
 * Application Layer는 이를 ProductInfo로 변환하여 외부에 노출한다.
 */
public record ProductDetail(
    Long id,
    String name,
    int price,
    String brandName,
    int stockQuantity,
    long likeCount
) {}
