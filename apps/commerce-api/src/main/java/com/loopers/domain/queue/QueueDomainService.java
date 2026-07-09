package com.loopers.domain.queue;

public class QueueDomainService {

    public long estimateWaitSeconds(long rank) {
        return rank / QueueThroughputPolicy.SAFE을_TPS;
    }
}
