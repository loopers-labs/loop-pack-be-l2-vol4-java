package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.domain.RankingSnapshot;
import com.loopers.ranking.infrastructure.RankingSnapshotJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 어제 ZSET 을 순위와 함께 ranking_snapshot 으로 확정한다(불변). 00:30 스케줄러가 호출한다.
 * 재실행 시 그날 스냅샷을 지우고 다시 써 멱등이다.
 */
@Service
@RequiredArgsConstructor
public class RankingFinalizeService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final RankingSnapshotJpaRepository snapshotRepository;

    @Transactional
    public void finalizeYesterday() {
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
        List<RankingEntry> entries = rankingRepository.readDesc(yesterday);
        if (entries.isEmpty()) {
            return;
        }

        snapshotRepository.deleteByStatDate(yesterday); // 재실행 시 교체(멱등)

        List<RankingSnapshot> snapshots = new ArrayList<>();
        int rankPosition = 1;
        for (RankingEntry entry : entries) {
            snapshots.add(new RankingSnapshot(yesterday, entry.productId(), rankPosition++, entry.score()));
        }
        snapshotRepository.saveAll(snapshots);
    }
}
