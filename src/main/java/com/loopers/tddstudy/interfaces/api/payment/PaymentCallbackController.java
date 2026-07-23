package com.loopers.tddstudy.interfaces.api.payment;


import com.loopers.tddstudy.domain.order.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentCallbackController {

    private final OrderRepository orderRepository;

    public PaymentCallbackController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Void> callback(@RequestBody PaymentCallbackRequest request) {
        String orderId = request.orderId();
        String status = request.status();

        orderRepository.findById(Long.parseLong(orderId))
                .ifPresent(order -> {
                    if ("SUCCESS".equals(status)) {
                        order.markPaid();
                    } else if ("FAILED".equals(status)) {
                        order.markFailed();
                    }
                    orderRepository.save(order);
                });

        return ResponseEntity.ok().build();
    }

}
