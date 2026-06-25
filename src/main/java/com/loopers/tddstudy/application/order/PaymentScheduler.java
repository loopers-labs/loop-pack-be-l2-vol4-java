package com.loopers.tddstudy.application.order;

import com.loopers.tddstudy.domain.order.Order;
import com.loopers.tddstudy.domain.order.OrderRepository;
import com.loopers.tddstudy.domain.order.PaymentGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
public class PaymentScheduler {
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final ExecutorService pgThreadPool;

    public PaymentScheduler(
            OrderRepository orderRepository,
            PaymentGateway paymentGateway,
            @Qualifier("pgThreadPool")ExecutorService pgThreadPool
    ) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.pgThreadPool = pgThreadPool;
    }

    @Scheduled(fixedDelay = 5000)
    public void processPendingPayments() {
        List<Order> pendingOrders = orderRepository.findAllByStatus("PENDING");

        List<CompletableFuture<Void>> futures = pendingOrders.stream()
                .map(order -> CompletableFuture.runAsync(() -> {
                    String result = paymentGateway.requestPayment(order.getId(), order.getTotalAmount());
                    if (!"PENDING".equals(result)) {
                        order.markFailed();
                        orderRepository.save(order);
                    }
                }, pgThreadPool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
