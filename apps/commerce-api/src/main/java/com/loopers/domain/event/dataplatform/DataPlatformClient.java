package com.loopers.domain.event.dataplatform;

import com.loopers.domain.event.order.OrderPaidEvent;

public interface DataPlatformClient {
    DataPlatformResult sendOrderPaid(OrderPaidEvent event);
}
