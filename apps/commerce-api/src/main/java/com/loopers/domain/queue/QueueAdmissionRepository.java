package com.loopers.domain.queue;

import java.time.Duration;
import java.util.List;

public interface QueueAdmissionRepository {

    record AdmittedEntry(Long userId, String token) {
    }

    // ZPOPMIN(대기열 제거)과 SET...EX(토큰 발급)를 Lua 스크립트 하나로 묶어 원자 실행한다 —
    // "대기열에도 없고 토큰도 없는" 중간 상태가 관측되지 않도록 보장하기 위함
    List<AdmittedEntry> admitBatch(int count, Duration tokenTtl);
}
