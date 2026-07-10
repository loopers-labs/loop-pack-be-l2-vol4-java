package com.loopers.application.product;

import com.loopers.domain.stock.StockModel;

/**
 * 재고 조회 결과 — 대기열 통과 후 품절 여부 확인용 경량 DTO.
 *
 * <p>{@code inStock}이 클라이언트의 주문 진행 판단 기준이다. {@code remainingStock}은 상세 조회와
 * 동일한 노출 정책(10개 이하일 때만 노출, 그 외 null)을 따른다.
 */
public record StockInfo(Long productId, boolean inStock, Integer remainingStock) {

    public static StockInfo from(Long productId, StockModel stock) {
        return new StockInfo(productId, stock.isAvailable(), stock.getDisplayQuantity());
    }
}
