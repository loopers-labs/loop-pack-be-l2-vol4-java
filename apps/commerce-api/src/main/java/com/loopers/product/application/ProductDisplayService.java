package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.product.domain.ProductDetail;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductSortType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Product + Brand + 좋아요 수를 조합하고 정렬하는 조회 조립 서비스.
 *
 * <p>Repository 에 의존하지 않는다. 호출자가 이미 로드한 도메인 객체를 받아 응답용 조회 모델 조립만 수행한다.
 */
public class ProductDisplayService {

    public ProductDetail assembleDetail(ProductModel product, BrandModel brand, long likeCount) {
        return toDetail(product, brand, likeCount);
    }

    public List<ProductDetail> assembleList(
        List<ProductModel> products,
        Map<Long, BrandModel> brandMap,
        Map<Long, Long> likeCountMap,
        ProductSortType sortType) {

        return products.stream()
            .map(product -> toDetail(
                product,
                brandMap.get(product.getBrandId()),
                likeCountMap.getOrDefault(product.getId(), 0L)))
            .sorted(comparator(sortType))
            .toList();
    }

    static ProductDetail toDetail(ProductModel product, BrandModel brand, long likeCount) {
        return new ProductDetail(
            product.getId(),
            product.getBrandId(),
            brand != null ? brand.getName() : null,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            likeCount);
    }

    static Comparator<ProductDetail> comparator(ProductSortType sortType) {
        return switch (sortType) {
            case PRICE_ASC -> Comparator.comparing(ProductDetail::price);
            case LIKES_DESC -> Comparator.comparingLong(ProductDetail::likeCount).reversed();
            case LATEST -> Comparator.comparing(ProductDetail::productId).reversed();
        };
    }
}
