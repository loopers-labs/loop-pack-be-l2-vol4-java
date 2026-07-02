package com.loopers.application.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.event.OrderPlaced;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class DataPlatformEventListener {

    private final DataPlatformSender dataPlatformSender;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        dataPlatformSender.send(DataPlatformPayload.from(event));
    }
}
