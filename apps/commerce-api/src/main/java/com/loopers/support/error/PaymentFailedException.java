package com.loopers.support.error;

/**
 * 결제 실패 전용 예외.
 *
 * <p>주문 생성 트랜잭션 안에서 결제 실패 시 throw 한다.
 * 결제 실패는 재고 차감·쿠폰 사용 등 선행 처리까지 모두 롤백하여 일관성을 유지한다.
 * 사용자에게는 400 BAD_REQUEST 로 결제 실패가 응답된다.
 */
public class PaymentFailedException extends CoreException {

    public PaymentFailedException(String message) {
        super(ErrorType.BAD_REQUEST, message);
    }
}
