package com.loopers.support.payment;

import org.junit.jupiter.api.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class PaymentWaitingRegistryTest {

    private PaymentWaitingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PaymentWaitingRegistry();
    }

    @DisplayName("등록한 transactionKey로 pop하면 future를 반환한다.")
    @Test
    void pop_returnsFuture_whenRegistered() {
        CompletableFuture<String> future = new CompletableFuture<>();
        registry.register("txKey1", future);

        var result = registry.pop("txKey1");

        assertTrue(result.isPresent());
        assertSame(future, result.get());
    }

    @DisplayName("pop은 get + remove — 두 번 호출 시 두 번째는 empty이다.")
    @Test
    void pop_returnsEmpty_whenCalledTwice() {
        CompletableFuture<String> future = new CompletableFuture<>();
        registry.register("txKey1", future);

        registry.pop("txKey1");
        var result = registry.pop("txKey1");

        assertTrue(result.isEmpty());
    }

    @DisplayName("등록하지 않은 key로 pop하면 empty를 반환한다.")
    @Test
    void pop_returnsEmpty_whenNotRegistered() {
        assertTrue(registry.pop("unknown").isEmpty());
    }
}
