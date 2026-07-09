package com.loopers.domain.queue;

import org.springframework.stereotype.Component;

@Component
public class QueueDomainService {

    public long estimateWaitSeconds(long rank) {
        return rank / QueueThroughputPolicy.SAFE_TPS;
    }
}
