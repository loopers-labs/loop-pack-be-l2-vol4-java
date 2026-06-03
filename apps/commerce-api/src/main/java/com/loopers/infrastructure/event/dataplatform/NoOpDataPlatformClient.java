package com.loopers.infrastructure.event.dataplatform;

import com.loopers.domain.event.dataplatform.DataPlatformClient;
import com.loopers.domain.event.dataplatform.DataPlatformResult;
import com.loopers.domain.event.order.OrderPaidEvent;
import org.springframework.stereotype.Component;

@Component
public class NoOpDataPlatformClient implements DataPlatformClient {

    @Override
    public DataPlatformResult sendOrderPaid(OrderPaidEvent event) {
        return DataPlatformResult.success();
    }
}
