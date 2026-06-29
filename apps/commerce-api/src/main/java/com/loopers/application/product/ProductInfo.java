package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.stock.StockModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 정보 DTO (Application 계층 출력).
 *
 * <p>단일 조합 ({@code forXxx}) + 목록 어셈블 ({@code assembleXxxList}) 정적 팩토리를 제공한다.
 * ApplicationService는 데이터 조회만 담당하고, 다중 원천 데이터를 DTO 리스트로 묶는 책임은 본 클래스가 진다.
 *
 * <ul>
 *   <li>사용자 상세 — 브랜드명 + 재고 표시 정책(inStock, remainingStock) + 좋아요 수</li>
 *   <li>사용자 목록 — 브랜드명 제외 + 재고 표시 정책 + 좋아요 수</li>
 *   <li>어드민 — 실수량(stock) 노출, 표시 정책/좋아요는 제외</li>
 * </ul>
 */
public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    boolean inStock,
    Integer remainingStock,
    Long likeCount
) {
    /** 사용자 상세 화면용 (브랜드명 + 재고 표시 정책 + 좋아요 수). 좋아요 수는 비정규화 컬럼 사용. */
    public static ProductInfo forUser(ProductWithBrand pwb, StockModel stockModel) {
        ProductModel product = pwb.product();
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            pwb.brand().getName(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            null,
            stockModel.isAvailable(),
            stockModel.getDisplayQuantity(),
            product.getLikeCount()
        );
    }

    /** 사용자 목록 화면용 (브랜드명 없이, 재고 표시 정책 + 좋아요 수). 좋아요 수는 비정규화 컬럼 사용. */
    public static ProductInfo forUserList(ProductModel product, StockModel stockModel) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            null,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            null,
            stockModel.isAvailable(),
            stockModel.getDisplayQuantity(),
            product.getLikeCount()
        );
    }

    /** 어드민용 (실재고 수량 노출, 좋아요 수 미포함) */
    public static ProductInfo forAdmin(ProductModel product, StockModel stockModel) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            null,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            stockModel.getQuantity(),
            stockModel.isAvailable(),
            null,
            null
        );
    }

    /**
     * 사용자 목록 어셈블 — 상품 + 재고 + 좋아요 수를 묶어 {@link ProductInfo} 리스트로 반환.
     *
     * <p>ApplicationService가 일괄 조회한 결과를 받아 productId 기준 매핑·조립을 수행한다.
     * 좋아요 0개인 상품은 {@code likeCountMap}에 없을 수 있어 {@code getOrDefault(id, 0L)}로 안전 조회한다.
     */
    public static List<ProductInfo> assembleUserList(
        List<ProductModel> products,
        List<StockModel> stocks
    ) {
        Map<Long, StockModel> stockMap = stocks.stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
        return products.stream()
            .map(p -> forUserList(p, Optional.ofNullable(stockMap.get(p.getId()))
                .orElseThrow(() -> new IllegalStateException(
                    "[productId=" + p.getId() + "] 재고 정보 누락 — 상품-재고 정합성 오류"))))
            .toList();
    }

    /** 캐시된 상세 정보의 재고 필드만 최신 DB 값으로 교체한다. 재고는 캐시에서 분리되어 항상 실시간 조회된다. */
    public ProductInfo withStock(StockModel stockModel) {
        return new ProductInfo(
            id, brandId, brandName, name, description, price,
            stock, stockModel.isAvailable(), stockModel.getDisplayQuantity(), likeCount
        );
    }

    /**
     * 어드민 목록 어셈블 — 상품 + 재고를 묶어 {@link ProductInfo} 리스트로 반환.
     *
     * <p>좋아요 수는 어드민 화면에 노출하지 않으므로 포함하지 않는다.
     */
    public static List<ProductInfo> assembleAdminList(
        List<ProductModel> products,
        List<StockModel> stocks
    ) {
        Map<Long, StockModel> stockMap = stocks.stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
        return products.stream()
            .map(p -> forAdmin(p, stockMap.get(p.getId())))
            .toList();
    }
}
