package com.loopers.domain.stock.event;

/**
 * 재고가 '절대 상태'로 바뀌었다는 사실의 통보(과거형). 누적 delta 가 아니라 변경된 절대 수량을 싣는다.
 * version 은 소비자가 순서역전·재전송 상황에서 "더 큰 version 만 반영"(최신성 가드)하도록 하는 기준.
 */
public record StockChangedEvent(Long productId, long quantity, long version) {

    public static StockChangedEvent of(Long productId, long quantity, long version) {
        return new StockChangedEvent(productId, quantity, version);
    }
}
