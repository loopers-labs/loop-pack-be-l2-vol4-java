package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 영속 스냅샷(ranking_daily_snapshot)에서 과거 랭킹을 읽는 어댑터. TTL 2일이 지나 ZSET 이 사라진 날짜를 담당한다.
 *
 * <p>{@link com.loopers.domain.ranking.RankingRepository} 를 직접 구현하지 않는다 — 포트 구현은
 * {@link RankingCompositeRepository} 하나이고, 이 클래스는 그 뒤의 한쪽 소스일 뿐이다.
 *
 * <p><b>상위 N 한계</b>: 배치가 상위 N(설정값, 기본 100)만 적재하므로 과거 날짜는 그 범위까지만 조회된다.
 * 따라서 {@code size()} 도 실제 그날 랭킹 크기가 아니라 <b>보존된 행 수</b>다(최대 N).
 */
@Repository
@RequiredArgsConstructor
public class RankingSnapshotRepository {

    private final RankingSnapshotJpaRepository rankingSnapshotJpaRepository;

    public List<RankedProduct> findPage(LocalDate date, int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();
        }
        return rankingSnapshotJpaRepository
                .findByRankingDateOrderByRankNoAsc(date, PageRequest.of(page - 1, size))
                .stream()
                .map(e -> new RankedProduct(e.getRankNo(), e.getProductId(), e.getScore()))
                .toList();
    }

    /** 보존된 행 수(최대 상위 N). 실제 그날 랭킹 크기가 아님 — 위 주석 참조. */
    public long size(LocalDate date) {
        return rankingSnapshotJpaRepository.countByRankingDate(date);
    }
}
