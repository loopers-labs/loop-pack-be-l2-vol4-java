package com.loopers.domain.ranking.batch;

import java.time.Duration;

/**
 * 같은 period+periodKey에 대한 rankingProductMvJob 동시 실행을 막기 위한 분산락 포트.
 * token은 락 소유자를 식별해, 소유자만 자신의 락을 해제할 수 있도록 한다.
 */
public interface RankingBatchLock {

    boolean tryLock(String key, String token, Duration ttl);

    void unlock(String key, String token);
}