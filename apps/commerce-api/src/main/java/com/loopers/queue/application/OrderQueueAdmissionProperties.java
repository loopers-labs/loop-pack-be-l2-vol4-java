package com.loopers.queue.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 스케줄러 발급 튜닝 노브. 목표 처리량(TPS)은 이 둘에서 파생된다. */
@ConfigurationProperties("order-queue.admission")
public record OrderQueueAdmissionProperties(long intervalMs, int batchSize) {

    /** 초당 발급 목표. 예상 대기시간 계산과 SSOT를 공유한다. */
    public double tps() {
        return batchSize * 1000.0 / intervalMs;
    }
}
