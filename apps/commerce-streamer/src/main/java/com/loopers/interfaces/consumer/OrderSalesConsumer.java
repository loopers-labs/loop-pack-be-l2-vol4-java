package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.ProductSalesApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order-events(판매 확정)를 소비해 product_metrics.sales_count 로 집계하는 collector.
 *
 * <p>catalog collector 와 <b>별도 groupId·별도 consumer 스레드</b>다(도메인별 토픽 분리). 같은 상품이 조회+판매를
 * 동시에 받으면 두 collector 가 같은 product_metrics 행을 건드리지만, {@code @DynamicUpdate} 가 컬럼 단위로 UPDATE 해
 * (sales collector 는 sales_count 만) 교차 lost update 를 막는다. record 리스너(공유 팩토리)라 실패는
 * {@code order-events.DLT} 로 격리된다.</p>
 */
@Component
@RequiredArgsConstructor
public class OrderSalesConsumer {

    public static final String ORDER_EVENTS = "order-events";

    private final ProductSalesApplicationService productSalesApplicationService;

    @KafkaListener(
            topics = ORDER_EVENTS,
            groupId = "product-sales-collector",
            containerFactory = KafkaErrorHandlingConfig.RECORD_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consume(OrderEventMessage message) {
        productSalesApplicationService.apply(message);
    }
}
