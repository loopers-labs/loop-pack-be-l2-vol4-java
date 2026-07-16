package com.loopers.domain.queue;

/**
 * 순번 조회 시 유저가 놓인 상태. 대기 중이거나(입장 대기), 이미 입장됐거나(토큰 보유) 둘 중 하나다.
 * sealed 로 두 변형만 허용해, 소비 측(application 매핑)이 모든 상태를 빠짐없이 처리하도록 컴파일러가 강제한다.
 */
public sealed interface QueueStatus permits QueueStatus.Waiting, QueueStatus.Admitted {

    /** 아직 대기열에서 차례를 기다리는 중. */
    record Waiting(QueuePosition position, long totalWaiting) implements QueueStatus {
    }

    /** 입장 처리되어 토큰을 발급받음(큐에서는 빠진 상태) = 내 차례. */
    record Admitted(EntryToken token) implements QueueStatus {
    }
}
