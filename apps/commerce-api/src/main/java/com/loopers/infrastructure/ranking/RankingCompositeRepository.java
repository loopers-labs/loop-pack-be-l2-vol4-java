package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@link RankingRepository} 의 유일한 포트 구현 — 날짜에 따라 <b>실시간 ZSET</b>과 <b>영속 스냅샷</b> 중
 * 하나를 고른다. 응용 계층(RankingFacade/ProductFacade)은 어느 쪽에서 왔는지 모른다.
 *
 * <p><b>출처는 날짜 단위로 정한다 — 페이지 단위로 폴백하면 안 된다.</b> "Redis 결과가 비면 스냅샷"으로 짜면,
 * Redis 에 그 날짜가 살아있는데 범위 밖 페이지를 요청했을 때(예: 3건뿐인데 page=10) 빈 결과를 보고 스냅샷으로
 * 넘어가 <b>엉뚱한 페이지를 반환</b>한다. 한 날짜의 결과가 두 소스에서 섞이면 순위가 뒤죽박죽이 된다.
 * 그래서 "그 날짜가 ZSET 에 존재하는가"(ZCARD>0)로 소스를 확정한다.
 *
 * <p><b>비용</b>: ZCARD 를 먼저 던지면 정상 경로(Redis 히트)에서 왕복이 하나 늘므로, ZREVRANGE 를 먼저 하고
 * <b>결과가 비었을 때만</b> ZCARD 로 "날짜가 없는 것"과 "페이지가 범위 밖인 것"을 가른다 → 정상 경로는 기존과 동일.
 *
 * <p><b>findRank 는 폴백하지 않는다</b>: 상품 상세는 오늘/어제만 조회하고(rank/rankYesterday) 그 두 날짜는
 * 항상 TTL 2일 안이라 ZSET 에 있다. 폴백을 넣으면 <b>랭킹에 없는 상품을 조회할 때마다</b> 헛된 DB 조회가 2회씩
 * 발생한다 — 상세는 hot path 라 그 비용이 크다. 어제 키가 없다는 건 어제 활동이 0이었다는 뜻이고, 그렇다면
 * 스냅샷에도 그 상품이 없다.
 */
@Primary
@Repository
@RequiredArgsConstructor
public class RankingCompositeRepository implements RankingRepository {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingSnapshotRepository rankingSnapshotRepository;

    @Override
    public List<RankedProduct> findPage(LocalDate date, int page, int size) {
        List<RankedProduct> live = rankingRedisRepository.findPage(date, page, size);
        if (!live.isEmpty()) {
            return live;
        }
        // 비었다 → 두 경우를 갈라야 한다.
        //  (a) 그 날짜가 ZSET 에 살아있는데 페이지가 범위 밖 → 빈 결과가 정답(스냅샷을 보면 안 된다)
        //  (b) 그 날짜가 ZSET 에 없다(TTL 만료/미적재) → 스냅샷이 소스
        if (rankingRedisRepository.size(date) > 0) {
            return List.of();
        }
        return rankingSnapshotRepository.findPage(date, page, size);
    }

    @Override
    public long size(LocalDate date) {
        long live = rankingRedisRepository.size(date);
        return live > 0 ? live : rankingSnapshotRepository.size(date);
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productId) {
        return rankingRedisRepository.findRank(date, productId); // 폴백 없음 — 위 주석 참조
    }
}
