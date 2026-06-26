package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgGateway;

import java.util.List;
import java.util.function.Supplier;

/**
 * PgGateway 테스트 대역(Fake) — 게이트웨이의 반환/예외 동작을 테스트가 직접 주입한다.
 *
 * <p>외부 pg-simulator HTTP 호출을 대체해, 게이트웨이가 던지는 예외/반환값에 따른
 * Application·스케줄러의 분기를 실제 DB 흐름 위에서 검증하기 위한 용도다.
 */
public class FakePgGateway implements PgGateway {

    /** requestPayment 호출 시 실행할 동작. 기본은 고정 transactionKey 반환. */
    public Supplier<String> requestBehavior = () -> "FAKE-TX-DEFAULT";

    /** findTransactionsByOrderId 가 반환할 트랜잭션 목록. 기본은 빈 목록. */
    public List<PgTransactionResult> transactions = List.of();

    @Override
    public String requestPayment(String userId, Long orderId, CardType cardType, String cardNo, Long amount, String callbackUrl) {
        return requestBehavior.get();
    }

    @Override
    public List<PgTransactionResult> findTransactionsByOrderId(String userId, String orderId) {
        return transactions;
    }
}
