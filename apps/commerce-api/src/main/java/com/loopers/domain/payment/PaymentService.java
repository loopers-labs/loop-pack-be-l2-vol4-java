package com.loopers.domain.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 트랜잭션 책임 Service.
 *
 * <p>pg-simulator 비동기 결제의 {@link PaymentModel} 상태 기록을 담당한다 — REQUESTED 생성,
 * TID 보존, PG 요청 실패 시 FAILED 전이. PG 호출 자체는 트랜잭션 밖에서 {@link PgGateway} 가
 * 수행하므로 이 Service 는 DB 상태 전이에만 집중한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 비동기 PG 결제 요청용 — REQUESTED 상태의 결제 기록을 생성한다.
     *
     * <p>pg-simulator 처럼 transactionKey 를 즉시 받고 결과는 콜백으로 수신하는 방식에서 사용.
     * 결제 시도 자체를 남기기 위해 PG 호출 전 먼저 저장한다.
     */
    @Transactional
    public void createRequested(Long orderId, Long amount) {
        paymentRepository.save(new PaymentModel(orderId, amount));
    }

    /**
     * PG 즉시 응답으로 받은 transactionKey 를 PaymentModel 에 저장한다.
     *
     * <p>비동기 결제에서 PG 가 PENDING 상태와 함께 즉시 반환하는 TID.
     * 콜백 미수신 시 스케줄러가 이 TID 로 직접 PG 조회할 수 있도록 보존한다.
     */
    @Transactional
    public void storePendingTransactionKey(Long orderId, String transactionKey) {
        paymentRepository.findByOrderId(orderId).ifPresentOrElse(
            p -> p.storePendingTransactionKey(transactionKey),
            () -> log.warn("[Payment] TID 저장 대상 결제 기록 없음 — orderId={}", orderId));
    }

    /**
     * PG 요청 자체가 실패했을 때 REQUESTED → FAILED 전이.
     *
     * <p>FeignException, CB open, fallback 등 PG 호출 예외 시 호출한다.
     */
    @Transactional
    public void markFailedOnRequest(Long orderId, String reason) {
        paymentRepository.findByOrderId(orderId).ifPresentOrElse(
            p -> p.markFailed(reason),
            () -> log.warn("[Payment] 실패 처리 대상 결제 기록 없음 — orderId={}, reason={}", orderId, reason));
    }
}
