package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgConnectionException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgTransactionDetail;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.interfaces.api.ApiResponse;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PG 호출 어댑터. Feign 예외를 도메인 예외로 변환한다.
 * <ul>
 *   <li>연결 실패(connection refused) → {@link PgConnectionException} (재시도 안전)</li>
 *   <li>read timeout / HTTP 오류 등 → {@link PgClientException} (도달 여부 불확실, 재시도 금지)</li>
 * </ul>
 * {@link Retry} 는 {@link PgConnectionException} 에만 동작한다(application.yml 의 retry.instances.pg).
 */
@Component
@RequiredArgsConstructor
public class PgClientImpl implements PgClient {

    private static final String RESILIENCE_INSTANCE = "pg";

    private final PgFeignClient pgFeignClient;

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    public PgPaymentResult requestPayment(PgPaymentCommand command) {
        PgV1Dto.PaymentRequest request = new PgV1Dto.PaymentRequest(
            command.orderId(),
            command.cardType().name(),
            command.cardNo(),
            command.amount(),
            command.callbackUrl()
        );
        try {
            PgV1Dto.TransactionResponse data = unwrap(pgFeignClient.requestPayment(command.userId(), request));
            return new PgPaymentResult(data.transactionKey(), toStatus(data.status()), data.reason());
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    public Optional<PgTransactionDetail> getTransaction(String userId, String transactionKey) {
        try {
            PgV1Dto.TransactionDetailResponse data = unwrap(pgFeignClient.getTransaction(userId, transactionKey));
            return Optional.of(toDetail(data));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    public List<PgTransactionDetail> findTransactionsByOrderId(String userId, String orderId) {
        try {
            PgV1Dto.OrderResponse data = unwrap(pgFeignClient.findByOrderId(userId, orderId));
            return data.transactions().stream()
                .map(tx -> new PgTransactionDetail(
                    tx.transactionKey(),
                    orderId,
                    null,
                    null,
                    null,
                    toStatus(tx.status()),
                    tx.reason()
                ))
                .toList();
        } catch (FeignException.NotFound e) {
            return Collections.emptyList();
        } catch (FeignException e) {
            throw translate(e);
        }
    }

    /** 연결 실패는 재시도 가능 예외로, 그 외(read timeout, HTTP 오류)는 일반 PG 예외로 변환한다. */
    private PgClientException translate(FeignException e) {
        if (e instanceof RetryableException && e.getCause() instanceof ConnectException) {
            return new PgConnectionException("PG 연결에 실패했습니다.", e);
        }
        return new PgClientException("PG 호출에 실패했습니다.", e);
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null || response.data() == null) {
            throw new PgClientException("PG 응답 본문이 비어 있습니다.");
        }
        return response.data();
    }

    private PgTransactionDetail toDetail(PgV1Dto.TransactionDetailResponse data) {
        return new PgTransactionDetail(
            data.transactionKey(),
            data.orderId(),
            toCardType(data.cardType()),
            data.cardNo(),
            data.amount(),
            toStatus(data.status()),
            data.reason()
        );
    }

    private PgTransactionStatus toStatus(String status) {
        try {
            return PgTransactionStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PgClientException("알 수 없는 PG 결제 상태입니다: " + status, e);
        }
    }

    private CardType toCardType(String cardType) {
        try {
            return CardType.valueOf(cardType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PgClientException("알 수 없는 카드 종류입니다: " + cardType, e);
        }
    }
}
