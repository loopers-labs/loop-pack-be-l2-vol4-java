package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 결제 승인 직전(자원 점유) 단계에서 재고 부족으로 확정 실패했을 때 던지는 예외.
 *
 * <p>일반 {@link CoreException}(쿠폰 만료 등 다른 점유 실패)과 구분해야 하는 이유 —
 * 대기열 토큰 정리 정책이 실패 사유별로 다르다: 재고 소진(품절)은 토큰을 즉시 삭제하고
 * 재대기를 요구하지만, 그 외 실패는 TTL이 남아있는 한 재시도를 허용한다.
 * 타입으로 구분해야 호출부가 메시지 문자열을 파싱하지 않고 분기할 수 있다.
 */
public class StockShortageException extends CoreException {

    private final Long productId;

    public StockShortageException(Long productId) {
        super(ErrorType.CONFLICT, "[productId = " + productId + "] 재고가 부족하여 주문을 진행할 수 없습니다.");
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
