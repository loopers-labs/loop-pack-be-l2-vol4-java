package com.loopers.support.payment;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리(힙) 기반의 결제 콜백 대기 레지스트리.
 * <p>
 * 이 레지스트리는 단일 애플리케이션 인스턴스 내에서만 long-polling future와 PG 콜백을 연결한다.
 * 멀티 인스턴스 배포 시 PG 콜백이 future를 등록한 인스턴스와 다른 노드에 도달하면
 * {@link #pop}이 빈 Optional을 반환하며, 이 경우 {@code PaymentApplicationService}의
 * timeout → poll 경로가 safety-net / source-of-truth 역할을 한다.
 */
@Component
public class PaymentWaitingRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> registry = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void register(String transactionKey, CompletableFuture<T> future) {
        registry.put(transactionKey, (CompletableFuture<Object>) future);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<CompletableFuture<T>> pop(String transactionKey) {
        return Optional.ofNullable((CompletableFuture<T>) registry.remove(transactionKey));
    }
}
