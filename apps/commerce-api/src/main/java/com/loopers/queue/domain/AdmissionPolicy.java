package com.loopers.queue.domain;

import java.time.Duration;

/** 대기열 안내 계산 규칙. 순번과 처리량만 받아 계산하는 순수 정책. */
public final class AdmissionPolicy {

    private AdmissionPolicy() {
    }

    /** 예상 대기시간 = 순번 / 처리량(TPS), 올림. */
    public static Duration estimatedWait(long rank, double tps) {
        long seconds = (long) Math.ceil(rank / tps);
        return Duration.ofSeconds(seconds);
    }

    /** 다음 폴링 간격. 순번이 뒤일수록 길게 잡아 조회 부하를 줄인다. */
    public static Duration pollInterval(long rank) {
        if (rank < 100) {
            return Duration.ofSeconds(1);
        }
        if (rank < 1000) {
            return Duration.ofSeconds(3);
        }
        return Duration.ofSeconds(5);
    }
}
