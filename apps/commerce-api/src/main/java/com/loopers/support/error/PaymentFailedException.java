package com.loopers.support.error;

/**
 * 결제 실패 전용 예외.
 *
 * <p>주문 생성 트랜잭션 안에서 결제 실패 시 throw 한다. 일반 {@link CoreException} 과 달리
 * {@code @Transactional(noRollbackFor = PaymentFailedException.class)} 와 함께 사용되어
 * <strong>throw 되더라도 트랜잭션이 롤백되지 않는다</strong>.
 *
 * <p>이를 통해 보상 처리(주문 CANCELLED + 재고 복구)는 커밋되어 감사·추적 기록으로 남고,
 * 사용자에게는 400 BAD_REQUEST 로 결제 실패가 응답된다.
 */
public class PaymentFailedException extends CoreException {

    public PaymentFailedException(String message) {
        super(ErrorType.BAD_REQUEST, message);
    }
}
