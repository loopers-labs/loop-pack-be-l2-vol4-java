package com.loopers.application.order;
import com.loopers.domain.order.*;
import com.loopers.support.error.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
// Hides: the transaction boundary and persistence sequencing for coupon application.
@Component @RequiredArgsConstructor
public class OrderDiscountFacade {
    private final OrderRepository repository; private final OrderDiscountService service;
    @Transactional public OrderDiscountInfo apply(long orderId,long buyerId,long couponId,Instant startedAt){
        Order order=repository.find(orderId).orElseThrow(()->new CoreException(ErrorType.NOT_FOUND,"order not found"));
        service.apply(order,buyerId,couponId,startedAt); return OrderDiscountInfo.from(repository.save(order));
    }
}
