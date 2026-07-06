package com.loopers.queue.domain;

import java.time.Duration;

public interface AdmissionLock {

    /** 발급 락을 시도한다. 잡으면 true, 이미 다른 인스턴스가 잡고 있으면 false. TTL 후 자동 해제된다. */
    boolean tryAcquire(Duration ttl);
}
